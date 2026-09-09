package com.carddemo.card.validator;

import com.carddemo.card.exception.CardValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * FR-L2..FR-L6, FR-D1, FR-D3..FR-D7, FR-U5, FR-U6 coverage for the account/card key
 * edits (COCRDLIC.cbl:1003-1066, COCRDSLC.cbl:608-720, COCRDUPC.cbl:721-799).
 */
class CardSearchKeyValidatorTest {

    private final CardSearchKeyValidator validator = new CardSearchKeyValidator();

    @Test
    void frL2_blankOrZeroAccountFilterMeansNoFilter() {
        assertThat(validator.optionalAccountFilter(null)).isNull();
        assertThat(validator.optionalAccountFilter("   ")).isNull();
        // CC-ACCT-ID-N EQUAL ZEROS is "not supplied", not "invalid".
        assertThat(validator.optionalAccountFilter("00000000000")).isNull();
    }

    @Test
    void frL3_frL5_accountFilterMustBeExactly11Digits() {
        assertThat(validator.optionalAccountFilter("00000000050")).isEqualTo("00000000050");

        // PIC X(11) tested with IS NUMERIC: a short entry is space padded and fails.
        assertThatThrownBy(() -> validator.optionalAccountFilter("50"))
                .isInstanceOf(CardValidationException.class)
                .hasMessage("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER");
        assertThatThrownBy(() -> validator.optionalAccountFilter("0000000005A"))
                .hasMessage("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER");
    }

    @Test
    void frL4_frL6_cardFilterMustBeExactly16Digits() {
        assertThat(validator.optionalCardFilter("0500024453765740")).isEqualTo("0500024453765740");
        assertThat(validator.optionalCardFilter("")).isNull();

        assertThatThrownBy(() -> validator.optionalCardFilter("50002445376574"))
                .isInstanceOf(CardValidationException.class)
                .hasMessage("CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER");
    }

    @Test
    void frD5_frU5_bothKeysBlankGivesNoInputReceived() {
        assertThatThrownBy(() -> validator.validateSearchKeys(null, null))
                .isInstanceOf(CardValidationException.class)
                .hasMessage("No input received");
        // '*' is normalised to LOW-VALUES, not treated as a wildcard (quirk Q-6).
        assertThatThrownBy(() -> validator.validateSearchKeys("*", "*"))
                .hasMessage("No input received");
    }

    @Test
    void frD3_frD4_theMissingKeyIsReportedAccountFirst() {
        assertThatThrownBy(() -> validator.validateSearchKeys("  ", "0500024453765740"))
                .hasMessage("Account number not provided");
        assertThatThrownBy(() -> validator.validateSearchKeys("00000000050", " "))
                .hasMessage("Card number not provided");
    }

    @Test
    void frD6_frD7_malformedKeysUseTheFilterMessages() {
        assertThatThrownBy(() -> validator.validateSearchKeys("123", "0500024453765740"))
                .hasMessage("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER");
        assertThatThrownBy(() -> validator.validateSearchKeys("00000000050", "05000244537657"))
                .hasMessage("CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER");
    }

    @Test
    void frD1_bothKeysWellFormedPasses() {
        assertThatCode(() -> validator.validateSearchKeys("00000000050", "0500024453765740"))
                .doesNotThrowAnyException();
    }
}
