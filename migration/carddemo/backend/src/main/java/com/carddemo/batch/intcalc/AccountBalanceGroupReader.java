package com.carddemo.batch.intcalc;

import com.carddemo.common.domain.TransactionCategoryBalanceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.support.SingleItemPeekableItemReader;

import java.util.ArrayList;
import java.util.List;

/**
 * The TCATBALF browse of CBACT04C (CBACT04C.cbl:188-206), delivering one account
 * at a time.
 *
 * <p>The COBOL reads the KSDS record by record and detects the account break by
 * comparing {@code TRANCAT-ACCT-ID} with {@code WS-LAST-ACCT-NUM}; here the same
 * key-ordered stream is folded into one item per account, so the per-account state
 * the COBOL held in WORKING-STORAGE lives inside a single chunk item and a restart
 * resumes on an account boundary. Each record is logged as the COBOL displayed it
 * (CBACT04C.cbl:193).
 *
 * <p>The peek is what identifies the final account of the run, the one the COBOL
 * never updates (FR-I16).
 */
class AccountBalanceGroupReader implements ItemStreamReader<AccountBalanceGroup> {

    private static final Logger log = LoggerFactory.getLogger("CBACT04C");

    private final SingleItemPeekableItemReader<TransactionCategoryBalanceRecord> delegate;

    AccountBalanceGroupReader(ItemStreamReader<TransactionCategoryBalanceRecord> delegate) {
        this.delegate = new SingleItemPeekableItemReader<>();
        this.delegate.setDelegate(delegate);
    }

    @Override
    public AccountBalanceGroup read() throws Exception {
        TransactionCategoryBalanceRecord first = readAndLog();
        if (first == null) {
            return null;
        }
        long acctId = first.getId().getAcctId();
        List<TransactionCategoryBalanceRecord> balances = new ArrayList<>();
        balances.add(first);

        while (true) {
            TransactionCategoryBalanceRecord next = delegate.peek();
            if (next == null) {
                return new AccountBalanceGroup(acctId, balances, true);
            }
            if (next.getId().getAcctId() != acctId) {
                return new AccountBalanceGroup(acctId, balances, false);
            }
            balances.add(readAndLog());
        }
    }

    private TransactionCategoryBalanceRecord readAndLog() throws Exception {
        TransactionCategoryBalanceRecord record = delegate.read();
        if (record != null) {
            log.info("{}{}{}{}",
                    String.format("%011d", record.getId().getAcctId()),
                    record.getId().getTypeCd(),
                    String.format("%04d", record.getId().getCatCd()),
                    record.getBal().toPlainString());
        }
        return record;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        delegate.open(executionContext);
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        delegate.update(executionContext);
    }

    @Override
    public void close() throws ItemStreamException {
        delegate.close();
    }
}
