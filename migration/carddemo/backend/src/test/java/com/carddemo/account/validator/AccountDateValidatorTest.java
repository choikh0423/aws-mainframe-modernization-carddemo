package com.carddemo.account.validator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** FR-AU-19..FR-AU-23: the CSUTLDPY date edits. */
class AccountDateValidatorTest {

    private final AccountDateValidator validator = new AccountDateValidator();

    @Test
    void yearIsMandatoryFourDigitsAndOfThisOrLastCentury() {
        assertThat(validator.validate("Open Date", "", "06", "08"))
                .isEqualTo("Open Date : Year must be supplied.");
        assertThat(validator.validate("Open Date", "19x1", "06", "08"))
                .isEqualTo("Open Date must be 4 digit number.");
        assertThat(validator.validate("Open Date", "1861", "06", "08"))
                .isEqualTo("Open Date : Century is not valid.");
    }

    @Test
    void monthAndDayAreRangeChecked() {
        assertThat(validator.validate("Open Date", "2014", "", "20"))
                .isEqualTo("Open Date : Month must be supplied.");
        assertThat(validator.validate("Open Date", "2014", "13", "20"))
                .isEqualTo("Open Date: Month must be a number between 1 and 12.");
        assertThat(validator.validate("Open Date", "2014", "11", ""))
                .isEqualTo("Open Date : Day must be supplied.");
        assertThat(validator.validate("Open Date", "2014", "11", "32"))
                .isEqualTo("Open Date:day must be a number between 1 and 31.");
    }

    @Test
    void dayMonthCombinationsFollowTheCalendar() {
        assertThat(validator.validate("Open Date", "2014", "11", "31"))
                .isEqualTo("Open Date:Cannot have 31 days in this month.");
        assertThat(validator.validate("Open Date", "2014", "02", "30"))
                .isEqualTo("Open Date:Cannot have 30 days in this month.");
        assertThat(validator.validate("Open Date", "2015", "02", "29"))
                .isEqualTo("Open Date:Not a leap year.Cannot have 29 days in this month.");
        assertThat(validator.validate("Open Date", "2016", "02", "29")).isNull();
        assertThat(validator.validate("Open Date", "2000", "02", "29")).isNull();
        assertThat(validator.validate("Open Date", "1900", "02", "29"))
                .isEqualTo("Open Date:Not a leap year.Cannot have 29 days in this month.");
    }

    @Test
    void dateOfBirthMustBeStrictlyInThePast() {
        LocalDate today = LocalDate.of(2024, 5, 20);
        assertThat(validator.notInFuture("Date of Birth", "1961", "06", "08", today)).isNull();
        assertThat(validator.notInFuture("Date of Birth", "2024", "05", "20", today))
                .isEqualTo("Date of Birth:cannot be in the future");
        assertThat(validator.notInFuture("Date of Birth", "2024", "05", "21", today))
                .isEqualTo("Date of Birth:cannot be in the future");
    }
}
