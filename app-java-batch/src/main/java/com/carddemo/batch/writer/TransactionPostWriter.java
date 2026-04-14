package com.carddemo.batch.writer;

import com.carddemo.batch.model.Account;
import com.carddemo.batch.model.CardXref;
import com.carddemo.batch.model.Transaction;
import com.carddemo.batch.model.TransactionCategoryBalance;
import com.carddemo.batch.repository.AccountRepository;
import com.carddemo.batch.repository.CardXrefRepository;
import com.carddemo.batch.repository.TransactionCategoryBalanceRepository;
import com.carddemo.batch.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Posts validated transactions to the database.
 * Ported from CBTRN02C.cbl paragraphs:
 *   2000-POST-TRANSACTION - orchestrates the posting
 *   2700-UPDATE-TCATBAL (lines 467-499) - update/create category balance
 *   2800-UPDATE-ACCOUNT-REC (lines 501-516) - update account balances
 *   2900-WRITE-TRANSACTION-FILE (lines 517-534) - write transaction record
 *
 * All operations within a @Transactional boundary.
 */
@Component
public class TransactionPostWriter implements ItemWriter<Transaction> {

    private static final Logger log = LoggerFactory.getLogger(TransactionPostWriter.class);

    private final TransactionRepository transactionRepository;
    private final TransactionCategoryBalanceRepository tcatbalRepository;
    private final AccountRepository accountRepository;
    private final CardXrefRepository cardXrefRepository;

    private final AtomicLong transactionIdCounter = new AtomicLong(-1);

    public TransactionPostWriter(
            TransactionRepository transactionRepository,
            TransactionCategoryBalanceRepository tcatbalRepository,
            AccountRepository accountRepository,
            CardXrefRepository cardXrefRepository) {
        this.transactionRepository = transactionRepository;
        this.tcatbalRepository = tcatbalRepository;
        this.accountRepository = accountRepository;
        this.cardXrefRepository = cardXrefRepository;
    }

    @Override
    @Transactional
    public void write(Chunk<? extends Transaction> items) {
        for (Transaction transaction : items) {
            // Generate transaction ID (find max existing ID, increment by 1)
            String tranId = generateTransactionId();
            transaction.setTranId(tranId);

            // Lookup the account via CardXref
            Optional<CardXref> xrefOpt = cardXrefRepository.findByCardNum(
                    transaction.getCardNum() != null ? transaction.getCardNum().trim() : "");
            if (xrefOpt.isEmpty()) {
                log.error("CardXref not found for card: {} - skipping post", transaction.getCardNum());
                continue;
            }
            Long acctId = xrefOpt.get().getAcctId();

            // 2700-UPDATE-TCATBAL: Update or create TransactionCategoryBalance
            updateTransactionCategoryBalance(acctId, transaction);

            // 2800-UPDATE-ACCOUNT-REC: Update account balances
            updateAccountRecord(acctId, transaction);

            // 2900-WRITE-TRANSACTION-FILE: Save the transaction
            transactionRepository.save(transaction);
            log.debug("Posted transaction: {}", tranId);
        }
    }

    /**
     * Generates a new 16-character transaction ID by incrementing from the max existing ID.
     * Ported from CBTRN02C.cbl paragraph 2100-GENERATE-TRAN-ID.
     */
    private synchronized String generateTransactionId() {
        if (transactionIdCounter.get() < 0) {
            Optional<Transaction> maxTran = transactionRepository.findTopByOrderByTranIdDesc();
            long maxId = 0;
            if (maxTran.isPresent()) {
                try {
                    maxId = Long.parseLong(maxTran.get().getTranId().trim());
                } catch (NumberFormatException e) {
                    log.warn("Could not parse max tran ID: {}", maxTran.get().getTranId());
                }
            }
            transactionIdCounter.set(maxId);
        }
        long nextId = transactionIdCounter.incrementAndGet();
        return String.format("%016d", nextId);
    }

    /**
     * Updates TransactionCategoryBalance: find by (acctId, tranTypeCd, tranCatCd).
     * If not found, create new record. Add tranAmt to balance.
     * Ported from 2700-UPDATE-TCATBAL / 2700-A-CREATE-TCATBAL-REC / 2700-B-UPDATE-TCATBAL-REC.
     */
    private void updateTransactionCategoryBalance(Long acctId, Transaction transaction) {
        Optional<TransactionCategoryBalance> tcatbalOpt =
                tcatbalRepository.findByAcctIdAndTranTypeCdAndTranCatCd(
                        acctId, transaction.getTranTypeCd(), transaction.getTranCatCd());

        TransactionCategoryBalance tcatbal;
        if (tcatbalOpt.isPresent()) {
            // 2700-B-UPDATE-TCATBAL-REC: ADD DALYTRAN-AMT TO TRAN-CAT-BAL
            tcatbal = tcatbalOpt.get();
            BigDecimal currentBalance = tcatbal.getBalance() != null
                    ? tcatbal.getBalance() : BigDecimal.ZERO;
            tcatbal.setBalance(currentBalance.add(
                    transaction.getTranAmt() != null ? transaction.getTranAmt() : BigDecimal.ZERO));
        } else {
            // 2700-A-CREATE-TCATBAL-REC: Create new record with tranAmt as balance
            tcatbal = new TransactionCategoryBalance();
            tcatbal.setAcctId(acctId);
            tcatbal.setTranTypeCd(transaction.getTranTypeCd());
            tcatbal.setTranCatCd(transaction.getTranCatCd());
            tcatbal.setBalance(transaction.getTranAmt() != null
                    ? transaction.getTranAmt() : BigDecimal.ZERO);
            log.debug("Creating new TCATBAL record for acct:{} type:{} cat:{}",
                    acctId, transaction.getTranTypeCd(), transaction.getTranCatCd());
        }
        tcatbalRepository.save(tcatbal);
    }

    /**
     * Updates account balances to reflect the posted transaction.
     * Ported from 2800-UPDATE-ACCOUNT-REC:
     *   ADD DALYTRAN-AMT TO ACCT-CURR-BAL
     *   IF DALYTRAN-AMT >= 0 ADD to ACCT-CURR-CYC-CREDIT
     *   ELSE ADD to ACCT-CURR-CYC-DEBIT
     */
    private void updateAccountRecord(Long acctId, Transaction transaction) {
        Optional<Account> accountOpt = accountRepository.findById(acctId);
        if (accountOpt.isEmpty()) {
            log.error("Account not found for update: {} (reason 109)", acctId);
            return;
        }

        Account account = accountOpt.get();
        BigDecimal tranAmt = transaction.getTranAmt() != null
                ? transaction.getTranAmt() : BigDecimal.ZERO;

        // ADD DALYTRAN-AMT TO ACCT-CURR-BAL
        BigDecimal currentBalance = account.getCurrentBalance() != null
                ? account.getCurrentBalance() : BigDecimal.ZERO;
        account.setCurrentBalance(currentBalance.add(tranAmt));

        // IF DALYTRAN-AMT >= 0 ADD TO ACCT-CURR-CYC-CREDIT ELSE ADD TO ACCT-CURR-CYC-DEBIT
        if (tranAmt.compareTo(BigDecimal.ZERO) >= 0) {
            BigDecimal cycleCredit = account.getCurrentCycleCredit() != null
                    ? account.getCurrentCycleCredit() : BigDecimal.ZERO;
            account.setCurrentCycleCredit(cycleCredit.add(tranAmt));
        } else {
            BigDecimal cycleDebit = account.getCurrentCycleDebit() != null
                    ? account.getCurrentCycleDebit() : BigDecimal.ZERO;
            account.setCurrentCycleDebit(cycleDebit.add(tranAmt));
        }

        accountRepository.save(account);
    }
}
