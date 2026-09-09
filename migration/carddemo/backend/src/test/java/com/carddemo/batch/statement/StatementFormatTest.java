package com.carddemo.batch.statement;

import com.carddemo.common.batch.FixedWidthRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-19, FR-24, FR-27, FR-29: the PICTURE clauses CBSTM03A prints with. */
class StatementFormatTest {

    @Test
    void movesToPicXLeftJustifiedAndBlankPadded() {
        assertThat(StatementFormat.text("ab", 5)).isEqualTo("ab   ");
        assertThat(StatementFormat.text("abcdef", 3)).isEqualTo("abc");
        assertThat(StatementFormat.text(null, 2)).isEqualTo("  ");
    }

    @Test
    void movesToPic9ZeroPaddedAndTruncatesHighOrderDigits() {
        assertThat(StatementFormat.digits(42L, 5)).isEqualTo("00042");
        assertThat(StatementFormat.digits(123456L, 4)).isEqualTo("3456");
    }

    @Test
    void writesZonedDecimalWithAnOverpunchSign() {
        assertThat(StatementFormat.zoned(new BigDecimal("13.75"), 11, 2)).isEqualTo("0000000137E");
        assertThat(StatementFormat.zoned(new BigDecimal("-13.75"), 11, 2)).isEqualTo("0000000137N");
    }

    @Test
    void zonedDecimalReadsBackThroughTheSharedFixedWidthDecoder() {
        String record = StatementFormat.text(StatementFormat.zoned(new BigDecimal("-13.75"), 11, 2), 11);
        assertThat(new FixedWidthRecord(record, 11).signed(0, 11, 2))
                .isEqualByComparingTo(new BigDecimal("-13.75"));
    }

    @Test
    void rendersPic9EditedBalances() {
        assertThat(StatementFormat.amountPic9(new BigDecimal("13.75"))).isEqualTo("000000013.75 ");
        assertThat(StatementFormat.amountPic9(new BigDecimal("-13.75"))).isEqualTo("000000013.75-");
        assertThat(StatementFormat.amountPic9(BigDecimal.ZERO)).isEqualTo("000000000.00 ");
    }

    @Test
    void rendersPicZEditedAmountsWithLeadingZeroSuppression() {
        assertThat(StatementFormat.amountPicZ(new BigDecimal("13.75"))).isEqualTo("       13.75 ");
        assertThat(StatementFormat.amountPicZ(new BigDecimal("-13.75"))).isEqualTo("       13.75-");
        assertThat(StatementFormat.amountPicZ(BigDecimal.ZERO)).isEqualTo("         .00 ");
    }

    @Test
    void truncatesEditedAmountsAboveNineIntegerDigits() {
        assertThat(StatementFormat.amountPic9(new BigDecimal("1234567890.12"))).isEqualTo("234567890.12 ");
    }

    @Test
    void stringDelimitedByBlankStopsAtTheFirstDelimiter() {
        assertThat(StatementFormat.upToBlank("John      ")).isEqualTo("John");
        assertThat(StatementFormat.upToDoubleBlank("John Q Public  ")).isEqualTo("John Q Public");
        assertThat(StatementFormat.upToDoubleBlank("nodelimiter")).isEqualTo("nodelimiter");
    }
}
