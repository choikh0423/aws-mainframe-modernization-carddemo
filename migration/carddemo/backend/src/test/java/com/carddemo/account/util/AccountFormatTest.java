package com.carddemo.account.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** FR-AV-10, FR-AV-11, FR-AV-12: the map editing of amounts, ids and the SSN. */
class AccountFormatTest {

    @Test
    void amountIsSignedBlankSuppressedAndCommaGrouped() {
        assertThat(AccountFormat.amount(new BigDecimal("194.00"))).isEqualTo("+        194.00");
        assertThat(AccountFormat.amount(new BigDecimal("13.75"))).isEqualTo("+         13.75");
        assertThat(AccountFormat.amount(new BigDecimal("-1234567.89"))).isEqualTo("-  1,234,567.89");
        assertThat(AccountFormat.amount(new BigDecimal("0"))).isEqualTo("+          0.00");
        assertThat(AccountFormat.amount(null)).isEqualTo("+          0.00");
    }

    @Test
    void amountAlwaysHasFifteenPositionsAndTwoDecimals() {
        assertThat(AccountFormat.amount(new BigDecimal("99999999.999"))).hasSize(15);
        assertThat(AccountFormat.amount(new BigDecimal("1.5"))).endsWith("1.50");
    }

    @Test
    void idsAreZeroPadded() {
        assertThat(AccountFormat.accountId(1L)).isEqualTo("00000000001");
        assertThat(AccountFormat.customerId(42L)).isEqualTo("000000042");
        assertThat(AccountFormat.fico(274)).isEqualTo("274");
    }

    @Test
    void ssnIsDisplayedWithDashes() {
        assertThat(AccountFormat.ssn(20973888L)).isEqualTo("020-97-3888");
    }

    @Test
    void slicesToleratePartialStoredValues() {
        assertThat(AccountFormat.datePart("1961-06-08", 5, 7)).isEqualTo("06");
        assertThat(AccountFormat.datePart("", 5, 7)).isEmpty();
        assertThat(AccountFormat.phonePart("(908)119-8310", 1, 4)).isEqualTo("908");
        assertThat(AccountFormat.phonePart(null, 1, 4)).isEmpty();
    }
}
