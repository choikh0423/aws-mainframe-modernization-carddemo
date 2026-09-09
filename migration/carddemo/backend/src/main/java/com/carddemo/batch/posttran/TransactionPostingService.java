package com.carddemo.batch.posttran;

import com.carddemo.common.batch.AbendService;
import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.DailyTransactionRecord;
import com.carddemo.common.domain.TransactionCategoryBalanceId;
import com.carddemo.common.domain.TransactionCategoryBalanceRecord;
import com.carddemo.common.domain.TransactionRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.TransactionCategoryBalanceRepository;
import com.carddemo.common.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * {@code 2000-POST-TRANSACTION} and its subordinates (CBTRN02C.cbl:424-579):
 * category balance, then account, then the write to the transaction master.
 *
 * <p>Runs inside the chunk transaction of {@code STEP15}, so the account and
 * category balance rows a record updates are the ones the preceding records of
 * the same run left behind, exactly as the COBOL re-read them from VSAM.
 */
@Service
public class TransactionPostingService {

    /** CBTRN02C.cbl:707-711 - CALL 'CEE3ABD' USING ABCODE(999), TIMING(0). */
    private static final String ABEND_CODE = "0999";
    private static final String ABEND_CULPRIT = "CBTRN02C";
    private static final String ABENDING_PROGRAM = "ABENDING PROGRAM";
    /** CBTRN02C.cbl:574 plus the file status a duplicate key write reports. */
    private static final String ERROR_WRITING_TO_TRANSACTION_FILE = "ERROR WRITING TO TRANSACTION FILE";
    private static final String DUPLICATE_KEY_FILE_STATUS = "FILE STATUS IS: NNNN0022";

    private static final Logger log = LoggerFactory.getLogger(TransactionPostingService.class);

    private final TransactionRepository transactionRepository;
    private final TransactionCategoryBalanceRepository categoryBalanceRepository;
    private final AccountRepository accountRepository;
    private final Db2TimestampFormatter timestampFormatter;
    private final AbendService abendService;

    public TransactionPostingService(TransactionRepository transactionRepository,
                                     TransactionCategoryBalanceRepository categoryBalanceRepository,
                                     AccountRepository accountRepository,
                                     Db2TimestampFormatter timestampFormatter,
                                     AbendService abendService) {
        this.transactionRepository = transactionRepository;
        this.categoryBalanceRepository = categoryBalanceRepository;
        this.accountRepository = accountRepository;
        this.timestampFormatter = timestampFormatter;
        this.abendService = abendService;
    }

    public void post(DailyTransactionRecord daily, ValidationResult validation) {
        TransactionRecord posted = toTransaction(daily);
        updateCategoryBalance(daily, validation.xref().getAcctId());
        updateAccount(daily, validation.account());
        writeTransaction(posted);
    }

    /** CBTRN02C.cbl:425-438 - every DALYTRAN field, plus a fresh processing timestamp. */
    private TransactionRecord toTransaction(DailyTransactionRecord daily) {
        TransactionRecord posted = new TransactionRecord();
        posted.setId(daily.getId());
        posted.setTypeCd(daily.getTypeCd());
        posted.setCatCd(daily.getCatCd());
        posted.setSource(daily.getSource());
        posted.setDescription(daily.getDescription());
        posted.setAmount(daily.getAmount());
        posted.setMerchantId(daily.getMerchantId());
        posted.setMerchantName(daily.getMerchantName());
        posted.setMerchantCity(daily.getMerchantCity());
        posted.setMerchantZip(daily.getMerchantZip());
        posted.setCardNum(daily.getCardNum());
        posted.setOrigTs(daily.getOrigTs());
        posted.setProcTs(timestampFormatter.now());
        return posted;
    }

    /** CBTRN02C.cbl:467-542 - 2700-UPDATE-TCATBAL and its create/update branches. */
    private void updateCategoryBalance(DailyTransactionRecord daily, Long acctId) {
        TransactionCategoryBalanceId key =
                new TransactionCategoryBalanceId(acctId, daily.getTypeCd(), daily.getCatCd());
        Optional<TransactionCategoryBalanceRecord> found = categoryBalanceRepository.findById(key);

        TransactionCategoryBalanceRecord balance;
        if (found.isEmpty()) {
            log.info("TCATBAL record not found for key : {}.. Creating.", categoryKeyImage(key));
            balance = new TransactionCategoryBalanceRecord();
            balance.setId(key);
            balance.setBal(BigDecimal.ZERO.setScale(2));
        } else {
            balance = found.get();
        }

        balance.setBal(balance.getBal().add(daily.getAmount()));
        categoryBalanceRepository.save(balance);
    }

    /** FD-TRAN-CAT-KEY as DISPLAY renders it: 9(11) + X(02) + 9(04). */
    private static String categoryKeyImage(TransactionCategoryBalanceId key) {
        return DailyTransactionImage.digits(key.getAcctId(), 11)
                + DailyTransactionImage.text(key.getTypeCd(), 2)
                + DailyTransactionImage.digits(key.getCatCd(), 4);
    }

    /** CBTRN02C.cbl:545-559 - 2800-UPDATE-ACCOUNT-REC. */
    private void updateAccount(DailyTransactionRecord daily, AccountRecord account) {
        BigDecimal amount = daily.getAmount();
        account.setCurrBal(account.getCurrBal().add(amount));
        if (amount.signum() >= 0) {
            account.setCurrCycCredit(account.getCurrCycCredit().add(amount));
        } else {
            account.setCurrCycDebit(account.getCurrCycDebit().add(amount));
        }
        accountRepository.save(account);
    }

    /** CBTRN02C.cbl:562-579 - 2900-WRITE-TRANSACTION-FILE. */
    private void writeTransaction(TransactionRecord posted) {
        if (transactionRepository.existsById(posted.getId())) {
            log.error(ERROR_WRITING_TO_TRANSACTION_FILE);
            log.error(DUPLICATE_KEY_FILE_STATUS);
            log.error(ABENDING_PROGRAM);
            throw abendService.abend(ABEND_CODE, ABEND_CULPRIT,
                    ERROR_WRITING_TO_TRANSACTION_FILE, DUPLICATE_KEY_FILE_STATUS);
        }
        transactionRepository.save(posted);
    }
}
