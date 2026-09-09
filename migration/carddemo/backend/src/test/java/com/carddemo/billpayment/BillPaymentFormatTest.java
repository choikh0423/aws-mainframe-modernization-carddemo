package com.carddemo.billpayment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-BP-22 / FR-BP-32 / FR-BP-35 / FR-BP-36 — the COBOL PICTURE edits COBIL00C
 * applies to the balance, the transaction id, the amount and the timestamp.
 */
class BillPaymentFormatTest {

    @Test
    void frBp22_currBalEdited() {
        assertThat(BillPaymentFormat.currBalEdited(new BigDecimal("194.00"))).isEqualTo("+0000000194.00");
        assertThat(BillPaymentFormat.currBalEdited(new BigDecimal("13.75"))).isEqualTo("+0000000013.75");
        assertThat(BillPaymentFormat.currBalEdited(new BigDecimal("0.00"))).isEqualTo("+0000000000.00");
        assertThat(BillPaymentFormat.currBalEdited(new BigDecimal("-3.5"))).isEqualTo("-0000000003.50");
        assertThat(BillPaymentFormat.currBalEdited(new BigDecimal("9999999999.99")))
                .isEqualTo("+9999999999.99");
    }

    @Test
    void frBp32_tranIdIsSixteenZeroPaddedDigits() {
        assertThat(BillPaymentFormat.tranId(1L)).isEqualTo("0000000000000001");
        assertThat(BillPaymentFormat.tranId(31L)).isEqualTo("0000000000000031");
    }

    @Test
    void frBp35_tranAmountTruncatesTheTenthDigit() {
        // Under 10^9 the balance is paid in full.
        assertThat(BillPaymentFormat.tranAmount(new BigDecimal("194.00")))
                .isEqualByComparingTo("194.00");
        // S9(10)V99 -> S9(09)V99 drops the high-order digit (quirk Q-1).
        assertThat(BillPaymentFormat.tranAmount(new BigDecimal("1234567890.12")))
                .isEqualByComparingTo("234567890.12");
        // The sign survives the MOVE.
        assertThat(BillPaymentFormat.tranAmount(new BigDecimal("-1234567890.12")))
                .isEqualByComparingTo("-234567890.12");
    }

    @Test
    void frBp36_timestampHasBlankSeparatorAndZeroMicroseconds() {
        assertThat(BillPaymentFormat.timestamp(LocalDateTime.of(2026, 2, 3, 4, 5, 6, 123_000_000)))
                .isEqualTo("2026-02-03 04:05:06.000000");
    }
}
