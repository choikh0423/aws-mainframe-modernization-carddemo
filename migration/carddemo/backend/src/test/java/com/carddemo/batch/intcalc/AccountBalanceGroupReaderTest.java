package com.carddemo.batch.intcalc;

import com.carddemo.common.domain.TransactionCategoryBalanceId;
import com.carddemo.common.domain.TransactionCategoryBalanceRecord;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-I3, FR-I4, FR-I16: the TCATBALF browse folded into one item per account. */
class AccountBalanceGroupReaderTest {

    @Test
    void groupsAllCategoriesOfAnAccount() throws Exception {
        AccountBalanceGroupReader reader = readerOver(
                balance(1L, "01", 1), balance(1L, "01", 2), balance(1L, "02", 1),
                balance(2L, "01", 1));

        AccountBalanceGroup first = reader.read();

        assertThat(first.acctId()).isEqualTo(1L);
        assertThat(first.balances()).extracting(b -> b.getId().getTypeCd() + b.getId().getCatCd())
                .containsExactly("011", "012", "021");
    }

    @Test
    void readsInKeyOrder() throws Exception {
        AccountBalanceGroupReader reader = readerOver(
                balance(1L, "01", 1), balance(2L, "01", 1), balance(3L, "01", 1));

        assertThat(reader.read().acctId()).isEqualTo(1L);
        assertThat(reader.read().acctId()).isEqualTo(2L);
        assertThat(reader.read().acctId()).isEqualTo(3L);
        assertThat(reader.read()).isNull();
    }

    /** Only the final account is flagged, and that is the one CBACT04C never updates. */
    @Test
    void flagsTheLastAccountOfTheRun() throws Exception {
        AccountBalanceGroupReader reader = readerOver(
                balance(1L, "01", 1), balance(2L, "01", 1));

        assertThat(reader.read().lastGroupInRun()).isFalse();
        assertThat(reader.read().lastGroupInRun()).isTrue();
    }

    @Test
    void readsNothingFromAnEmptyFile() throws Exception {
        assertThat(readerOver().read()).isNull();
    }

    private static AccountBalanceGroupReader readerOver(TransactionCategoryBalanceRecord... records) {
        AccountBalanceGroupReader reader = new AccountBalanceGroupReader(new ListStreamReader(List.of(records)));
        reader.open(new ExecutionContext());
        return reader;
    }

    private static TransactionCategoryBalanceRecord balance(long acctId, String typeCd, int catCd) {
        TransactionCategoryBalanceRecord record = new TransactionCategoryBalanceRecord();
        record.setId(new TransactionCategoryBalanceId(acctId, typeCd, catCd));
        record.setBal(new BigDecimal("100.00"));
        return record;
    }

    /** A stand-in for the JPA paging reader over TCATBALF. */
    private static final class ListStreamReader implements ItemStreamReader<TransactionCategoryBalanceRecord> {

        private final Iterator<TransactionCategoryBalanceRecord> items;

        private ListStreamReader(List<TransactionCategoryBalanceRecord> items) {
            this.items = items.iterator();
        }

        @Override
        public TransactionCategoryBalanceRecord read() {
            return items.hasNext() ? items.next() : null;
        }

        @Override
        public void open(ExecutionContext executionContext) throws ItemStreamException {
        }

        @Override
        public void update(ExecutionContext executionContext) throws ItemStreamException {
        }

        @Override
        public void close() throws ItemStreamException {
        }
    }
}
