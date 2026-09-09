package com.carddemo.batch.statement;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-04, FR-05, FR-06: the SORT OUTREC that builds the COSTM01 work record. */
class TrnxLayoutTest {

    @Test
    void mapsTransactToTheCostm01WorkLayout() {
        String transact = StatementFixtures.transact("4111111111111111", "0000000000000042",
                new BigDecimal("13.75"));

        String work = TrnxLayout.fromTransact(transact);

        assertThat(work).hasSize(TrnxLayout.LENGTH);
        assertThat(TrnxLayout.cardNumber(work)).isEqualTo("4111111111111111");
        assertThat(TrnxLayout.transactionId(work)).isEqualTo("0000000000000042");
        assertThat(TrnxLayout.typeCode(work)).isEqualTo("01");
        assertThat(TrnxLayout.categoryCode(work)).isEqualTo(5);
        assertThat(TrnxLayout.description(work).trim()).isEqualTo("Coffee shop");
        assertThat(TrnxLayout.amount(work)).isEqualByComparingTo(new BigDecimal("13.75"));
        assertThat(TrnxLayout.merchantId(work)).isEqualTo(400000001L);
    }

    @Test
    void truncatesProcTimestampToTwentyFourCharacters() {
        String transact = StatementFixtures.transact("4111111111111111", "0000000000000042",
                new BigDecimal("13.75"));

        String work = TrnxLayout.fromTransact(transact);

        assertThat(TrnxLayout.originalTimestamp(work)).isEqualTo("2022-01-01 10:00:00.000000");
        assertThat(TrnxLayout.processingTimestamp(work)).isEqualTo("2022-01-02 10:00:00.9999  ");
    }

    @Test
    void reproRoundTripsTheWorkRecordThroughTheKeyedStore() {
        String work = StatementFixtures.work("4111111111111111", "0000000000000042",
                "Coffee shop", new BigDecimal("-13.75"));

        StatementWorkTransaction row = TrnxLayout.toWorkTransaction(work);

        assertThat(row.getCardNum()).isEqualTo("4111111111111111");
        assertThat(row.getTranId()).isEqualTo("0000000000000042");
        assertThat(row.getAmount()).isEqualByComparingTo(new BigDecimal("-13.75"));
        assertThat(TrnxLayout.render(row)).isEqualTo(work);
    }

    @Test
    void keyIsTheSixteenByteCardNumberFollowedByTheTransactionId() {
        String work = StatementFixtures.work("4111111111111111", "0000000000000042",
                "Coffee shop", BigDecimal.ONE);

        assertThat(work.substring(0, TrnxLayout.KEY_LENGTH))
                .isEqualTo("4111111111111111" + "0000000000000042");
    }
}
