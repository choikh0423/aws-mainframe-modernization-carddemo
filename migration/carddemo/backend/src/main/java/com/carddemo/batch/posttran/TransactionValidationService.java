package com.carddemo.batch.posttran;

import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.DailyTransactionRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.CardXrefRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * {@code 1500-VALIDATE-TRAN} and its subordinates (CBTRN02C.cbl:370-422).
 *
 * <p>The cross reference is read first and the account, credit limit and
 * expiration checks only run when it succeeded, so an unknown card can only ever
 * be reported as 100.
 */
@Service
public class TransactionValidationService {

    /** 1500-A-LOOKUP-XREF, CBTRN02C.cbl:385-387. */
    public static final int REASON_INVALID_CARD = 100;
    public static final String INVALID_CARD_NUMBER_FOUND = "INVALID CARD NUMBER FOUND";

    /** 1500-B-LOOKUP-ACCT, CBTRN02C.cbl:397-399. */
    public static final int REASON_ACCOUNT_NOT_FOUND = 101;
    public static final String ACCOUNT_RECORD_NOT_FOUND = "ACCOUNT RECORD NOT FOUND";

    /** 1500-B-LOOKUP-ACCT, CBTRN02C.cbl:410-412. */
    public static final int REASON_OVERLIMIT = 102;
    public static final String OVERLIMIT_TRANSACTION = "OVERLIMIT TRANSACTION";

    /** 1500-B-LOOKUP-ACCT, CBTRN02C.cbl:417-419. */
    public static final int REASON_EXPIRED = 103;
    public static final String TRANSACTION_RECEIVED_AFTER_ACCT_EXPIRATION =
            "TRANSACTION RECEIVED AFTER ACCT EXPIRATION";

    /** ACCT-EXPIRAION-DATE is PIC X(10) and is compared against DALYTRAN-ORIG-TS (1:10). */
    private static final int DATE_LENGTH = 10;

    private final CardXrefRepository cardXrefRepository;
    private final AccountRepository accountRepository;

    public TransactionValidationService(CardXrefRepository cardXrefRepository,
                                        AccountRepository accountRepository) {
        this.cardXrefRepository = cardXrefRepository;
        this.accountRepository = accountRepository;
    }

    public ValidationResult validate(DailyTransactionRecord daily) {
        Optional<CardXrefRecord> xref = cardXrefRepository.findById(daily.getCardNum());
        if (xref.isEmpty()) {
            return ValidationResult.rejected(REASON_INVALID_CARD, INVALID_CARD_NUMBER_FOUND, null, null);
        }

        Optional<AccountRecord> found = accountRepository.findById(xref.get().getAcctId());
        if (found.isEmpty()) {
            return ValidationResult.rejected(
                    REASON_ACCOUNT_NOT_FOUND, ACCOUNT_RECORD_NOT_FOUND, xref.get(), null);
        }
        AccountRecord account = found.get();

        ValidationResult result = ValidationResult.accepted(xref.get(), account);

        BigDecimal cycleBalance = account.getCurrCycCredit()
                .subtract(account.getCurrCycDebit())
                .add(daily.getAmount());
        if (account.getCreditLimit().compareTo(cycleBalance) < 0) {
            result = ValidationResult.rejected(
                    REASON_OVERLIMIT, OVERLIMIT_TRANSACTION, xref.get(), account);
        }

        // Not an ELSE of the overlimit check in the COBOL either: a record that is both
        // overlimit and expired is reported as 103 only.
        if (alphanumeric(account.getExpiraionDate()).compareTo(transactionDate(daily)) < 0) {
            result = ValidationResult.rejected(
                    REASON_EXPIRED, TRANSACTION_RECEIVED_AFTER_ACCT_EXPIRATION, xref.get(), account);
        }

        return result;
    }

    /** DALYTRAN-ORIG-TS (1:10): the first ten bytes of the X(26) field. */
    private static String transactionDate(DailyTransactionRecord daily) {
        return alphanumeric(daily.getOrigTs());
    }

    private static String alphanumeric(String value) {
        return DailyTransactionImage.text(value, DATE_LENGTH);
    }
}
