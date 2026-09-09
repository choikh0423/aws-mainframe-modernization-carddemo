package com.carddemo.pendingauth.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-F1..FR-F5: the edited pictures the CPVS/CPVD maps and the CP00 reply carry.
 */
class PendingAuthFormatTest {

    @Test
    void frF1_amount12MatchesPicMinusZzzzzzz9Dot99() {
        assertThat(PendingAuthFormat.amount12(new BigDecimal("13.75"))).isEqualTo("       13.75");
        assertThat(PendingAuthFormat.amount12(new BigDecimal("-13.75"))).isEqualTo("-      13.75");
        assertThat(PendingAuthFormat.amount12(BigDecimal.ZERO)).isEqualTo("        0.00");
        assertThat(PendingAuthFormat.amount12(null)).isEqualTo("        0.00");
        assertThat(PendingAuthFormat.amount12(new BigDecimal("12345678.90")))
                .isEqualTo(" 12345678.90");
    }

    @Test
    void frF2_amount9MatchesPicMinusZzzz9Dot99() {
        assertThat(PendingAuthFormat.amount9(new BigDecimal("262.55"))).isEqualTo("   262.55");
        assertThat(PendingAuthFormat.amount9(new BigDecimal("-1.05"))).isEqualTo("-    1.05");
    }

    @Test
    void frF3_amount14IsTheCp00ReplyPicture() {
        assertThat(PendingAuthFormat.amount14(new BigDecimal("13.75")))
                .isEqualTo("         13.75");
        assertThat(PendingAuthFormat.amount14(BigDecimal.ZERO)).isEqualTo("          0.00");
    }

    @Test
    void frF4_countIsThreeZeroPaddedDigits() {
        assertThat(PendingAuthFormat.count3(6)).isEqualTo("006");
        assertThat(PendingAuthFormat.count3(null)).isEqualTo("000");
        assertThat(PendingAuthFormat.count3(1234)).isEqualTo("234");
    }

    @Test
    void frF5_dateTimeAndExpiryEditing() {
        assertThat(PendingAuthFormat.displayDate("250115")).isEqualTo("01/15/25");
        assertThat(PendingAuthFormat.displayDate("")).isEmpty();
        assertThat(PendingAuthFormat.displayTime("150000")).isEqualTo("15:00:00");
        assertThat(PendingAuthFormat.displayTime(null)).isEmpty();
        assertThat(PendingAuthFormat.displayExpiry("2605")).isEqualTo("26/05");
        assertThat(PendingAuthFormat.displayExpiry("26")).isEmpty();
    }

    @Test
    void zeroPaddedIdsMatchTheMapFields() {
        assertThat(PendingAuthFormat.acctId11(1L)).isEqualTo("00000000001");
        assertThat(PendingAuthFormat.custId9(1L)).isEqualTo("000000001");
        assertThat(PendingAuthFormat.code9(13)).isEqualTo("000000013");
    }
}
