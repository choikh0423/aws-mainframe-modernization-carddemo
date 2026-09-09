package com.carddemo.batch.tranreport;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-14-021: the report's edited amounts.
 *
 * <p>The expected strings are what GnuCOBOL produces for {@code MOVE} into
 * {@code PIC -ZZZ,ZZZ,ZZZ.ZZ} and {@code PIC +ZZZ,ZZZ,ZZZ.ZZ}.
 */
class TranReportPicturesTest {

    @Test
    void detailAmountSuppressesLeadingZerosAndInsertsCommas() {
        assertThat(TranReportPictures.detailAmount(new BigDecimal("183.80"))).isEqualTo("         183.80");
        assertThat(TranReportPictures.detailAmount(new BigDecimal("1307.70"))).isEqualTo("       1,307.70");
        assertThat(TranReportPictures.detailAmount(new BigDecimal("123456789.01"))).isEqualTo(" 123,456,789.01");
    }

    @Test
    void detailAmountShowsOnlyTheNegativeSign() {
        assertThat(TranReportPictures.detailAmount(new BigDecimal("-1307.70"))).isEqualTo("-      1,307.70");
        assertThat(TranReportPictures.detailAmount(new BigDecimal("-0.75"))).isEqualTo("-           .75");
    }

    @Test
    void totalAmountAlwaysShowsASign() {
        assertThat(TranReportPictures.totalAmount(new BigDecimal("183.80"))).isEqualTo("+        183.80");
        assertThat(TranReportPictures.totalAmount(new BigDecimal("-1307.70"))).isEqualTo("-      1,307.70");
        assertThat(TranReportPictures.totalAmount(new BigDecimal("123456789.01"))).isEqualTo("+123,456,789.01");
    }

    /** Every digit position is a suppression symbol, so zero blanks the field. */
    @Test
    void zeroBlanksTheWholeField() {
        assertThat(TranReportPictures.detailAmount(BigDecimal.ZERO)).isEqualTo(" ".repeat(15));
        assertThat(TranReportPictures.totalAmount(new BigDecimal("0.00"))).isEqualTo(" ".repeat(15));
    }

    /** A value under a dollar suppresses everything up to the decimal point. */
    @Test
    void suppressionStopsAtTheDecimalPoint() {
        assertThat(TranReportPictures.detailAmount(new BigDecimal("0.05"))).isEqualTo("            .05");
        assertThat(TranReportPictures.totalAmount(new BigDecimal("0.05"))).isEqualTo("+           .05");
    }

    /** A MOVE truncates the digits that do not fit S9(09)V99 rather than rounding. */
    @Test
    void truncatesRatherThanRoundsOrOverflows() {
        assertThat(TranReportPictures.detailAmount(new BigDecimal("1.999"))).isEqualTo("           1.99");
        assertThat(TranReportPictures.detailAmount(new BigDecimal("1234567890.12"))).isEqualTo(" 234,567,890.12");
    }
}
