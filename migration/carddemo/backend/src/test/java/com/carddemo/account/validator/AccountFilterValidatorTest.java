package com.carddemo.account.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.account.exception.AccountFilterException;
import org.junit.jupiter.api.Test;

/** FR-AV-03, FR-AV-04, FR-AU-04, FR-AQ-04: same edit, two different messages. */
class AccountFilterValidatorTest {

    private final AccountFilterValidator validator = new AccountFilterValidator();

    @Test
    void anElevenDigitNonZeroIdIsAccepted() {
        assertThat(validator.validateViewFilter("00000000001")).isEqualTo(1L);
        assertThat(validator.validateUpdateFilter("00000000001")).isEqualTo(1L);
    }

    @Test
    void blankGivesTheNoInputMessageOnBothScreens() {
        assertThatThrownBy(() -> validator.validateViewFilter("   "))
                .isInstanceOf(AccountFilterException.class)
                .hasMessage("No input received");
        assertThatThrownBy(() -> validator.validateUpdateFilter(null))
                .isInstanceOf(AccountFilterException.class)
                .hasMessage("No input received");
    }

    @Test
    void theTwoScreensWordTheInvalidFilterDifferently() {
        assertThatThrownBy(() -> validator.validateViewFilter("0000000000X"))
                .hasMessage("Account Filter must  be a non-zero 11 digit number");
        assertThatThrownBy(() -> validator.validateUpdateFilter("0000000000X"))
                .hasMessage("Account Number if supplied must be a 11 digit Non-Zero Number");
    }

    @Test
    void shortAndZeroIdsAreRejected() {
        assertThatThrownBy(() -> validator.validateViewFilter("1"))
                .hasMessage("Account Filter must  be a non-zero 11 digit number");
        assertThatThrownBy(() -> validator.validateViewFilter("00000000000"))
                .hasMessage("Account Filter must  be a non-zero 11 digit number");
    }
}
