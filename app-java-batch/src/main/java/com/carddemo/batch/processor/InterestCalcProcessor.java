package com.carddemo.batch.processor;

import com.carddemo.batch.model.Account;
import com.carddemo.batch.model.CardXref;
import com.carddemo.batch.model.DiscountGroup;
import com.carddemo.batch.model.Transaction;
import com.carddemo.batch.model.TransactionCategoryBalance;
import com.carddemo.batch.repository.AccountRepository;
import com.carddemo.batch.repository.CardXrefRepository;
import com.carddemo.batch.repository.DiscountGroupRepository;
import com.carddemo.batch.service.DateValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Computes interest for TransactionCategoryBalance records.
 * Ported from CBACT04C.cbl paragraphs:
 *   1100-GET-ACCT-DATA
 *   1110-GET-XREF-DATA
 *   1200-GET-INTEREST-RATE (with DEFAULT fallback)
 *   1300-COMPUTE-INTEREST: COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200
 *   1300-B-WRITE-TX: creates system-generated Transaction records
 */
@Component
public class InterestCalcProcessor
        implements ItemProcessor<TransactionCategoryBalance, Transaction> {

    private static final Logger log = LoggerFactory.getLogger(InterestCalcProcessor.class);
    private static final BigDecimal TWELVE_HUNDRED = new BigDecimal("1200");

    private final AccountRepository accountRepository;
    private final CardXrefRepository cardXrefRepository;
    private final DiscountGroupRepository discountGroupRepository;
    private final DateValidationService dateValidationService;
    private final AtomicLong tranIdSuffix = new AtomicLong(0);
    private String processDate;

    public InterestCalcProcessor(
            AccountRepository accountRepository,
            CardXrefRepository cardXrefRepository,
            DiscountGroupRepository discountGroupRepository,
            DateValidationService dateValidationService) {
        this.accountRepository = accountRepository;
        this.cardXrefRepository = cardXrefRepository;
        this.discountGroupRepository = discountGroupRepository;
        this.dateValidationService = dateValidationService;
    }

    public void setProcessDate(String processDate) {
        this.processDate = processDate;
        this.tranIdSuffix.set(0);
    }

    @Override
    public Transaction process(TransactionCategoryBalance item) {
        // 1100-GET-ACCT-DATA: look up account
        Optional<Account> accountOpt = accountRepository.findById(item.getAcctId());
        if (accountOpt.isEmpty()) {
            log.warn("Account not found: {} - skipping interest calc", item.getAcctId());
            return null;
        }
        Account account = accountOpt.get();

        // 1110-GET-XREF-DATA: look up card via xref by account ID
        List<CardXref> xrefs = cardXrefRepository.findByAcctId(item.getAcctId());
        if (xrefs.isEmpty()) {
            log.warn("CardXref not found for account: {} - skipping", item.getAcctId());
            return null;
        }
        CardXref xref = xrefs.get(0);

        // 1200-GET-INTEREST-RATE: look up discount group
        String groupId = account.getGroupId() != null ? account.getGroupId().trim() : "DEFAULT";
        Optional<DiscountGroup> discountOpt =
                discountGroupRepository.findByAcctGroupIdAndTranTypeCdAndTranCatCd(
                        groupId, item.getTranTypeCd(), item.getTranCatCd());

        // Try DEFAULT group if specific group not found
        if (discountOpt.isEmpty() && !"DEFAULT".equals(groupId)) {
            discountOpt = discountGroupRepository.findByAcctGroupIdAndTranTypeCdAndTranCatCd(
                    "DEFAULT", item.getTranTypeCd(), item.getTranCatCd());
        }

        if (discountOpt.isEmpty()) {
            log.warn("No discount group found for group:{} type:{} cat:{} - skipping",
                    groupId, item.getTranTypeCd(), item.getTranCatCd());
            return null;
        }

        BigDecimal intRate = discountOpt.get().getIntRate();
        BigDecimal balance = item.getBalance() != null ? item.getBalance() : BigDecimal.ZERO;

        // 1300-COMPUTE-INTEREST: COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200
        BigDecimal monthlyInt = balance.multiply(intRate)
                .divide(TWELVE_HUNDRED, 2, RoundingMode.HALF_UP);

        if (monthlyInt.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        // 1300-B-WRITE-TX: create system-generated Transaction
        long suffix = tranIdSuffix.incrementAndGet();
        String datePrefix = processDate != null ? processDate : "00000000";
        String tranId = String.format("%-8s%08d", datePrefix, suffix);
        if (tranId.length() > 16) {
            tranId = tranId.substring(0, 16);
        }

        Transaction transaction = new Transaction();
        transaction.setTranId(tranId);
        transaction.setTranTypeCd("01");
        transaction.setTranCatCd(5);
        transaction.setTranSource("System");
        transaction.setTranDesc("Int. for a/c " + item.getAcctId());
        transaction.setTranAmt(monthlyInt);
        transaction.setMerchantId(0L);
        transaction.setMerchantName("");
        transaction.setMerchantCity("");
        transaction.setMerchantZip("");
        transaction.setCardNum(xref.getCardNum());

        String timestamp = dateValidationService.generateDb2Timestamp();
        transaction.setOrigTimestamp(timestamp);
        transaction.setProcTimestamp(timestamp);

        return transaction;
    }
}
