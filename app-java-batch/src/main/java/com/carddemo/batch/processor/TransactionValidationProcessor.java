package com.carddemo.batch.processor;

import com.carddemo.batch.model.Account;
import com.carddemo.batch.model.CardXref;
import com.carddemo.batch.model.DailyTransaction;
import com.carddemo.batch.model.RejectRecord;
import com.carddemo.batch.service.AccountLookupService;
import com.carddemo.batch.service.CardXrefLookupService;
import com.carddemo.batch.service.DateValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.annotation.OnSkipInWrite;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Validates daily transactions before posting.
 * Ported from CBTRN02C.cbl paragraphs 1500-VALIDATE-TRAN,
 * 1500-A-LOOKUP-XREF (lines 380-392), and 1500-B-LOOKUP-ACCT (lines 393-422).
 *
 * Validation rules:
 *   100 - INVALID CARD NUMBER FOUND
 *   101 - ACCOUNT RECORD NOT FOUND
 *   102 - OVERLIMIT TRANSACTION
 *   103 - TRANSACTION RECEIVED AFTER ACCT EXPIRATION
 */
@Component
public class TransactionValidationProcessor
        implements ItemProcessor<DailyTransaction, DailyTransaction> {

    private static final Logger log = LoggerFactory.getLogger(TransactionValidationProcessor.class);

    private final CardXrefLookupService cardXrefLookupService;
    private final AccountLookupService accountLookupService;
    private final DateValidationService dateValidationService;

    public TransactionValidationProcessor(
            CardXrefLookupService cardXrefLookupService,
            AccountLookupService accountLookupService,
            DateValidationService dateValidationService) {
        this.cardXrefLookupService = cardXrefLookupService;
        this.accountLookupService = accountLookupService;
        this.dateValidationService = dateValidationService;
    }

    @Override
    public DailyTransaction process(DailyTransaction item) throws ValidationException {
        // 1500-A-LOOKUP-XREF: Look up card number in CardXref
        Optional<CardXref> xrefOpt = cardXrefLookupService.findByCardNum(
                item.getCardNum() != null ? item.getCardNum().trim() : "");
        if (xrefOpt.isEmpty()) {
            throw new ValidationException(100, "INVALID CARD NUMBER FOUND", item);
        }

        CardXref xref = xrefOpt.get();

        // 1500-B-LOOKUP-ACCT: Look up account by acctId from xref
        Optional<Account> accountOpt = accountLookupService.findById(xref.getAcctId());
        if (accountOpt.isEmpty()) {
            throw new ValidationException(101, "ACCOUNT RECORD NOT FOUND", item);
        }

        Account account = accountOpt.get();

        // Credit limit check: COMPUTE WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT
        //                                         - ACCT-CURR-CYC-DEBIT
        //                                         + DALYTRAN-AMT
        BigDecimal cycleCredit = account.getCurrentCycleCredit() != null
                ? account.getCurrentCycleCredit() : BigDecimal.ZERO;
        BigDecimal cycleDebit = account.getCurrentCycleDebit() != null
                ? account.getCurrentCycleDebit() : BigDecimal.ZERO;
        BigDecimal tranAmt = item.getTranAmt() != null
                ? item.getTranAmt() : BigDecimal.ZERO;
        BigDecimal tempBal = cycleCredit.subtract(cycleDebit).add(tranAmt);

        BigDecimal creditLimit = account.getCreditLimit() != null
                ? account.getCreditLimit() : BigDecimal.ZERO;
        if (creditLimit.compareTo(tempBal) < 0) {
            throw new ValidationException(102, "OVERLIMIT TRANSACTION", item);
        }

        // Expiration date check: IF ACCT-EXPIRAION-DATE >= DALYTRAN-ORIG-TS(1:10)
        if (!dateValidationService.isAccountNotExpired(
                account.getExpirationDate(), item.getOrigTimestamp())) {
            throw new ValidationException(103,
                    "TRANSACTION RECEIVED AFTER ACCT EXPIRATION", item);
        }

        return item;
    }

    /**
     * Custom validation exception carrying the reject reason code and description.
     */
    public static class ValidationException extends Exception {
        private final int reasonCode;
        private final String reasonDescription;
        private final DailyTransaction dailyTransaction;

        public ValidationException(int reasonCode, String reasonDescription,
                                   DailyTransaction dailyTransaction) {
            super(reasonDescription);
            this.reasonCode = reasonCode;
            this.reasonDescription = reasonDescription;
            this.dailyTransaction = dailyTransaction;
        }

        public int getReasonCode() {
            return reasonCode;
        }

        public String getReasonDescription() {
            return reasonDescription;
        }

        public DailyTransaction getDailyTransaction() {
            return dailyTransaction;
        }

        public RejectRecord toRejectRecord() {
            return new RejectRecord(
                    dailyTransaction.toFixedLengthString(),
                    reasonCode,
                    reasonDescription);
        }
    }
}
