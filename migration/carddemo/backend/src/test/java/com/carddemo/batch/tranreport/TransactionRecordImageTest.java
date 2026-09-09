package com.carddemo.batch.tranreport;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-14-002 and FR-14-003: the 350-byte TRANSACT image the unload step writes
 * and the fields CBTRN03C reads back out of it.
 */
class TransactionRecordImageTest {

    private static List<String> fixture() {
        return TransactFixture.images();
    }

    @Test
    void unloadsARowIntoTheColumnsTheSortStepAddresses() {
        String expected = fixture().get(0);
        String image = TransactionRecordImage.of(TransactFixture.row(expected));

        assertThat(image).hasSize(TransactionRecordImage.LENGTH);
        assertThat(image).isEqualTo(expected);
        // SYMNAMES TRAN-CARD-NUM,263,16 and TRAN-PROC-DT,305,10 (1-based).
        assertThat(image.substring(262, 278)).isEqualTo(expected.substring(262, 278));
        assertThat(image.substring(304, 314)).isEqualTo(expected.substring(304, 314));
    }

    @Test
    void roundTripsEveryFixtureRecord() {
        for (String record : fixture()) {
            assertThat(TransactionRecordImage.of(TransactFixture.row(record))).isEqualTo(record);
        }
    }

    @Test
    void parsesTheFieldsTheReportUses() {
        PostedTransaction transaction = TransactionRecordImage.parse(fixture().get(0));

        assertThat(transaction.id()).isEqualTo("0000000000683580");
        assertThat(transaction.typeCd()).isEqualTo("01");
        assertThat(transaction.catCd()).isEqualTo(1);
        assertThat(transaction.source()).isEqualTo("POS TERM");
        assertThat(transaction.amount()).isEqualByComparingTo("504.77");
        assertThat(transaction.cardNum()).isEqualTo("4859452612877065");
        assertThat(transaction.procDate()).isEqualTo("2022-07-01");
    }

    /** TRAN-CARD-NUM is PIC X(16), so the key keeps every byte of the field. */
    @Test
    void keepsTheCardNumberAtItsFullWidth() {
        assertThat(TransactionRecordImage.parse(fixture().get(0)).cardNum()).hasSize(16);
    }

    @Test
    void writesTheSignAsAZonedOverpunch() {
        assertThat(TransactionRecordImage.signed(new BigDecimal("504.77"), 11)).isEqualTo("0000005047G");
        assertThat(TransactionRecordImage.signed(new BigDecimal("-919.00"), 11)).isEqualTo("0000009190}");
        assertThat(TransactionRecordImage.signed(BigDecimal.ZERO, 11)).isEqualTo("0000000000{");
    }
}
