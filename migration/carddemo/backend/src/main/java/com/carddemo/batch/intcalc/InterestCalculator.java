package com.carddemo.batch.intcalc;

import com.carddemo.common.batch.AbendService;
import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.DisclosureGroupId;
import com.carddemo.common.domain.DisclosureGroupRecord;
import com.carddemo.common.domain.TransactionCategoryBalanceRecord;
import com.carddemo.common.domain.TransactionRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.CardXrefRepository;
import com.carddemo.common.repository.DisclosureGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The business logic of CBACT04C: for one account's category balances, the
 * interest transactions to write and the account rewrite to apply.
 *
 * <p>Paragraph map: {@code 1100-GET-ACCT-DATA} / {@code 1110-GET-XREF-DATA}
 * (CBACT04C.cbl:372-413), {@code 1200-GET-INTEREST-RATE} +
 * {@code 1200-A-GET-DEFAULT-INT-RATE} (:415-460), {@code 1300-COMPUTE-INTEREST}
 * and {@code 1300-B-WRITE-TX} (:462-515), {@code 1050-UPDATE-ACCOUNT} (:350-370).
 * {@code 1400-COMPUTE-FEES} (:518-520) is empty in the source, so nothing here
 * computes a fee.
 *
 * <p>Every message string is the COBOL literal, unchanged.
 */
class InterestCalculator {

    /** 12 months x 100, the divisor of the annual percentage rate. */
    private static final BigDecimal MONTHS_TIMES_PERCENT = new BigDecimal("1200");

    /** DIS-ACCT-GROUP-ID substituted when the account's own group has no row. */
    private static final String DEFAULT_GROUP_ID = "DEFAULT";

    private static final String ABEND_CODE = "0999";
    private static final String ABEND_CULPRIT = "CBACT04C";

    private static final Logger log = LoggerFactory.getLogger("CBACT04C");

    private final AccountRepository accounts;
    private final CardXrefRepository cardXrefs;
    private final DisclosureGroupRepository disclosureGroups;
    private final AbendService abendService;
    private final TranIdSequence tranIds;
    private final Clock clock;

    InterestCalculator(AccountRepository accounts,
                       CardXrefRepository cardXrefs,
                       DisclosureGroupRepository disclosureGroups,
                       AbendService abendService,
                       TranIdSequence tranIds,
                       Clock clock) {
        this.accounts = accounts;
        this.cardXrefs = cardXrefs;
        this.disclosureGroups = disclosureGroups;
        this.abendService = abendService;
        this.tranIds = tranIds;
        this.clock = clock;
    }

    InterestPosting post(AccountBalanceGroup group) {
        AccountRecord account = readAccount(group.acctId());
        CardXrefRecord xref = readCardXref(group.acctId());

        BigDecimal totalInterest = BigDecimal.ZERO.setScale(2);
        List<TransactionRecord> transactions = new ArrayList<>();

        for (TransactionCategoryBalanceRecord balance : group.balances()) {
            BigDecimal rate = interestRate(account.getGroupId(), balance);
            if (rate.signum() == 0) {
                continue;
            }
            BigDecimal monthlyInterest = monthlyInterest(balance.getBal(), rate);
            totalInterest = totalInterest.add(monthlyInterest);
            transactions.add(interestTransaction(group.acctId(), monthlyInterest, xref.getCardNum()));
        }

        return new InterestPosting(updatedAccount(group, account, totalInterest), transactions);
    }

    /**
     * {@code COMPUTE WS-MONTHLY-INT = ( TRAN-CAT-BAL * DIS-INT-RATE) / 1200}
     * (CBACT04C.cbl:464-465) into {@code PIC S9(09)V99} with no {@code ROUNDED}
     * phrase: the quotient is truncated toward zero at two decimals.
     */
    private BigDecimal monthlyInterest(BigDecimal balance, BigDecimal rate) {
        return balance.multiply(rate).divide(MONTHS_TIMES_PERCENT, 2, RoundingMode.DOWN);
    }

    /**
     * {@code 1200-GET-INTEREST-RATE}: the account group's rate, falling back once
     * to the {@code DEFAULT} group for the same transaction type and category.
     */
    private BigDecimal interestRate(String acctGroupId, TransactionCategoryBalanceRecord balance) {
        String typeCd = balance.getId().getTypeCd();
        Integer catCd = balance.getId().getCatCd();

        Optional<DisclosureGroupRecord> exact =
                disclosureGroups.findById(new DisclosureGroupId(acctGroupId, typeCd, catCd));
        if (exact.isPresent()) {
            return exact.get().getIntRate();
        }

        log.info("DISCLOSURE GROUP RECORD MISSING");
        log.info("TRY WITH DEFAULT GROUP CODE");
        return disclosureGroups.findById(new DisclosureGroupId(DEFAULT_GROUP_ID, typeCd, catCd))
                .orElseThrow(() -> {
                    log.error("ERROR READING DEFAULT DISCLOSURE GROUP");
                    return abendService.abend(ABEND_CODE, ABEND_CULPRIT,
                            "1200-A-GET-DEFAULT-INT-RATE file status 23",
                            "ERROR READING DEFAULT DISCLOSURE GROUP");
                })
                .getIntRate();
    }

    /** {@code 1300-B-WRITE-TX} (CBACT04C.cbl:473-498). */
    private TransactionRecord interestTransaction(long acctId, BigDecimal amount, String cardNum) {
        TransactionRecord tran = new TransactionRecord();
        tran.setId(tranIds.nextTranId());
        tran.setTypeCd("01");
        tran.setCatCd(5);
        tran.setSource("System");
        tran.setDescription("Int. for a/c " + String.format("%011d", acctId));
        tran.setAmount(amount);
        tran.setMerchantId(0L);
        tran.setMerchantName("");
        tran.setMerchantCity("");
        tran.setMerchantZip("");
        tran.setCardNum(cardNum);
        String timestamp = Db2Timestamp.now(clock);
        tran.setOrigTs(timestamp);
        tran.setProcTs(timestamp);
        return tran;
    }

    /**
     * {@code 1050-UPDATE-ACCOUNT} (CBACT04C.cbl:350-356), and the quirk that it is
     * never reached for the last account of the run (FR-I16): the loop at
     * CBACT04C.cbl:188 only enters its body while {@code END-OF-FILE = 'N'}, so the
     * {@code ELSE PERFORM 1050-UPDATE-ACCOUNT} at :219-220 is unreachable.
     */
    private AccountRecord updatedAccount(AccountBalanceGroup group,
                                         AccountRecord account,
                                         BigDecimal totalInterest) {
        if (group.lastGroupInRun()) {
            return null;
        }
        account.setCurrBal(account.getCurrBal().add(totalInterest));
        account.setCurrCycCredit(BigDecimal.ZERO.setScale(2));
        account.setCurrCycDebit(BigDecimal.ZERO.setScale(2));
        return account;
    }

    private AccountRecord readAccount(long acctId) {
        return accounts.findById(acctId).orElseThrow(() -> {
            log.error("ACCOUNT NOT FOUND: {}", String.format("%011d", acctId));
            log.error("ERROR READING ACCOUNT FILE");
            return abendService.abend(ABEND_CODE, ABEND_CULPRIT,
                    "1100-GET-ACCT-DATA file status 23", "ERROR READING ACCOUNT FILE");
        });
    }

    private CardXrefRecord readCardXref(long acctId) {
        return cardXrefs.findByAcctId(acctId).orElseThrow(() -> {
            log.error("ACCOUNT NOT FOUND: {}", String.format("%011d", acctId));
            log.error("ERROR READING XREF FILE");
            return abendService.abend(ABEND_CODE, ABEND_CULPRIT,
                    "1110-GET-XREF-DATA file status 23", "ERROR READING XREF FILE");
        });
    }
}
