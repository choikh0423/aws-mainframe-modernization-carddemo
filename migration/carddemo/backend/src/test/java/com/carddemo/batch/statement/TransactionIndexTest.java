package com.carddemo.batch.statement;

import com.carddemo.common.batch.AbendException;
import com.carddemo.common.batch.AbendService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-11, FR-12, FR-13, FR-14: WS-TRNX-TABLE grouping, its fixed capacities and
 * the 4000-TRNXFILE-GET scan.
 */
class TransactionIndexTest {

    @Test
    void groupsWorkRecordsByCardNumberStartingAtTheFirstRecord() {
        TransactionIndex index = index(
                work("4111111111111111", "1"),
                work("4111111111111111", "2"),
                work("4222222222222222", "3"));

        assertThat(index.cardCount()).isEqualTo(2);
        assertThat(ids(index.transactionsFor("4111111111111111"))).containsExactly("1", "2");
        assertThat(ids(index.transactionsFor("4222222222222222"))).containsExactly("3");
    }

    @Test
    void returnsNoTransactionsForACardThatHasNone() {
        TransactionIndex index = index(work("4111111111111111", "1"));

        assertThat(index.transactionsFor("4999999999999999")).isEmpty();
    }

    @Test
    void holdsAtMostTenTransactionsPerCard() {
        String[] records = new String[12];
        for (int i = 0; i < records.length; i++) {
            records[i] = work("4111111111111111", Integer.toString(i));
        }

        TransactionIndex index = index(records);

        assertThat(index.transactionsFor("4111111111111111")).hasSize(TransactionIndex.MAX_TRANSACTIONS);
        assertThat(index.overflowCount()).isEqualTo(2);
    }

    @Test
    void holdsAtMostFiftyOneCards() {
        String[] records = new String[60];
        for (int i = 0; i < records.length; i++) {
            records[i] = work(String.format("4%015d", i), "1");
        }

        TransactionIndex index = index(records);

        assertThat(index.cardCount()).isEqualTo(60);
        assertThat(index.overflowCount()).isEqualTo(60 - TransactionIndex.MAX_CARDS);
        assertThat(index.transactionsFor(String.format("4%015d", 51))).isEmpty();
    }

    @Test
    void stopsScanningOnceTheTableIsPastTheXrefCard() {
        TransactionIndex index = index(
                work("4111111111111111", "1"),
                work("4222222222222222", "2"));

        assertThat(index.transactionsFor("4000000000000000")).isEmpty();
    }

    @Test
    void abendsWhenTheWorkStoreIsEmpty() {
        StatementFileAccessStub files = new StatementFileAccessStub(List.of());

        assertThatThrownBy(() -> TransactionIndex.load(files, abend()))
                .isInstanceOf(AbendException.class)
                .extracting(failure -> ((AbendException) failure).getAbendData())
                .satisfies(data -> {
                    assertThat(data.abendMsg()).isEqualTo("ERROR READING TRNXFILE");
                    assertThat(data.abendReason()).isEqualTo(StatementFileArea.RC_END_OF_FILE);
                    assertThat(data.abendCulprit()).isEqualTo("CBSTM03A");
                });
    }

    private static StatementAbend abend() {
        return new StatementAbend(new AbendService());
    }

    private static TransactionIndex index(String... records) {
        return TransactionIndex.load(new StatementFileAccessStub(List.of(records)), abend());
    }

    private static List<String> ids(List<String> records) {
        return records.stream().map(record -> TrnxLayout.transactionId(record).trim()).toList();
    }

    private static String work(String cardNumber, String transactionId) {
        return StatementFixtures.work(cardNumber, transactionId, "Coffee shop", BigDecimal.ONE);
    }
}
