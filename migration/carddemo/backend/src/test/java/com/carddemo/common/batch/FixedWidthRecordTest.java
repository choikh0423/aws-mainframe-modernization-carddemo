package com.carddemo.common.batch;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Field extraction and overpunch decoding against real record fragments. */
class FixedWidthRecordTest {

    @Test
    void textStripsCobolTrailingPadding() {
        FixedWidthRecord record = new FixedWidthRecord("Purchase            ", 20);
        assertThat(record.text(0, 20)).isEqualTo("Purchase");
    }

    @Test
    void numberReadsZonedDecimalAndTreatsBlankAsZero() {
        assertThat(new FixedWidthRecord("00000000001", 11).number(0, 11)).isEqualTo(1L);
        assertThat(new FixedWidthRecord("           ", 11).number(0, 11)).isZero();
    }

    @Test
    void signedDecodesPositiveOverpunch() {
        // ACCT-CURR-BAL from app/data/ASCII/acctdata.txt record 1: 194.00
        assertThat(new FixedWidthRecord("00000001940{", 12).signed(0, 12, 2))
                .isEqualByComparingTo(new BigDecimal("194.00"));
        // TRAN-AMT from app/data/ASCII/dailytran.txt record 1: 504.77
        assertThat(new FixedWidthRecord("0000005047G", 11).signed(0, 11, 2))
                .isEqualByComparingTo(new BigDecimal("504.77"));
    }

    @Test
    void signedDecodesNegativeOverpunch() {
        assertThat(new FixedWidthRecord("0000005047P", 11).signed(0, 11, 2))
                .isEqualByComparingTo(new BigDecimal("-504.77"));
        assertThat(new FixedWidthRecord("00000000000}", 12).signed(0, 12, 2))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void signedAcceptsAPlainTrailingDigit() {
        assertThat(new FixedWidthRecord("000150", 6).signed(0, 6, 2))
                .isEqualByComparingTo(new BigDecimal("1.50"));
    }

    @Test
    void shortLinesArePaddedToTheRecordLength() {
        FixedWidthRecord record = new FixedWidthRecord("01", 60);
        assertThat(record.text(0, 2)).isEqualTo("01");
        assertThat(record.text(2, 50)).isEmpty();
    }
}
