package com.carddemo.account.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.account.AccountFieldsFixture;
import com.carddemo.account.dto.AccountFields;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * 1200-EDIT-MAP-INPUTS as a whole: FR-AU-09 (order), FR-AU-10 (only the first message survives)
 * and the field rules FR-AU-13..FR-AU-28.
 */
class AccountUpdateValidatorTest {

    private final AccountUpdateValidator validator = new AccountUpdateValidator(
            new AccountDateValidator(),
            Clock.fixed(LocalDate.of(2024, 5, 20).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));

    @Test
    void aCleanScreenProducesNoMessage() {
        assertThat(validator.validate(AccountFieldsFixture.validScreen())).isNull();
    }

    @Test
    void onlyTheFirstFailingEditIsReported() {
        AccountFields fields = AccountFieldsFixture.validScreen();
        fields.activeStatus = "X";
        fields.firstName = "Ann3";
        fields.ficoScore = "999";
        assertThat(validator.validate(fields)).isEqualTo("Account Status must be Y or N.");
    }

    @Test
    void editsRunInTheSourceOrder() {
        AccountFields fields = AccountFieldsFixture.validScreen();
        fields.openYear = "";
        fields.creditLimit = "abc";
        assertThat(validator.validate(fields)).isEqualTo("Open Date : Year must be supplied.");

        fields = AccountFieldsFixture.validScreen();
        fields.creditLimit = "abc";
        fields.expiryMonth = "13";
        assertThat(validator.validate(fields)).isEqualTo("Credit Limit is not valid");

        fields = AccountFieldsFixture.validScreen();
        fields.ssnPart1 = "";
        fields.dobYear = "";
        assertThat(validator.validate(fields)).isEqualTo("SSN: First 3 chars must be supplied.");
    }

    @Test
    void ssnFirstPartRejectsTheReservedRanges() {
        AccountFields fields = AccountFieldsFixture.validScreen();
        fields.ssnPart1 = "666";
        assertThat(validator.validate(fields))
                .isEqualTo("SSN: First 3 chars: should not be 000, 666, or between 900 and 999");
        fields.ssnPart1 = "912";
        assertThat(validator.validate(fields))
                .isEqualTo("SSN: First 3 chars: should not be 000, 666, or between 900 and 999");
        fields.ssnPart1 = "000";
        assertThat(validator.validate(fields)).isEqualTo("SSN: First 3 chars must not be zero.");
    }

    @Test
    void ficoScoreMustBeBetweenThreeHundredAndEightFifty() {
        AccountFields fields = AccountFieldsFixture.validScreen();
        fields.ficoScore = "274";
        assertThat(validator.validate(fields)).isEqualTo("FICO Score: should be between 300 and 850");
        fields.ficoScore = "851";
        assertThat(validator.validate(fields)).isEqualTo("FICO Score: should be between 300 and 850");
        fields.ficoScore = "300";
        assertThat(validator.validate(fields)).isNull();
        fields.ficoScore = "850";
        assertThat(validator.validate(fields)).isNull();
    }

    @Test
    void dateOfBirthCannotBeInTheFuture() {
        AccountFields fields = AccountFieldsFixture.validScreen();
        fields.dobYear = "2025";
        assertThat(validator.validate(fields)).isEqualTo("Date of Birth:cannot be in the future");
    }

    @Test
    void stateMustBeAKnownCodeAndMustMatchTheZip() {
        AccountFields fields = AccountFieldsFixture.validScreen();
        fields.state = "XX";
        assertThat(validator.validate(fields)).isEqualTo("State: is not a valid state code");

        fields = AccountFieldsFixture.validScreen();
        fields.zip = "99999";
        assertThat(validator.validate(fields)).isEqualTo("Invalid zip code for state");
    }

    @Test
    void phoneNumbersAreOptionalOnlyWhenEveryPartIsBlank() {
        AccountFields fields = AccountFieldsFixture.validScreen();
        fields.phone2Area = "";
        fields.phone2Prefix = "";
        fields.phone2Line = "";
        assertThat(validator.validate(fields)).isNull();

        fields = AccountFieldsFixture.validScreen();
        fields.phone1Area = "";
        assertThat(validator.validate(fields)).isEqualTo("Phone Number 1: Area code must be supplied.");

        fields = AccountFieldsFixture.validScreen();
        fields.phone1Prefix = "";
        assertThat(validator.validate(fields)).isEqualTo("Phone Number 1: Prefix code must be supplied.");

        fields = AccountFieldsFixture.validScreen();
        fields.phone1Line = "";
        assertThat(validator.validate(fields)).isEqualTo("Phone Number 1: Line number code must be supplied.");
    }

    @Test
    void phonePartsAreDigitsAndNonZeroAndTheAreaCodeMustExist() {
        AccountFields fields = AccountFieldsFixture.validScreen();
        fields.phone1Area = "12X";
        assertThat(validator.validate(fields))
                .isEqualTo("Phone Number 1: Area code must be A 3 digit number.");

        fields = AccountFieldsFixture.validScreen();
        fields.phone1Area = "000";
        assertThat(validator.validate(fields)).isEqualTo("Phone Number 1: Area code cannot be zero");

        fields = AccountFieldsFixture.validScreen();
        fields.phone1Area = "555";
        assertThat(validator.validate(fields))
                .isEqualTo("Phone Number 1: Not valid North America general purpose area code");

        fields = AccountFieldsFixture.validScreen();
        fields.phone1Line = "0000";
        assertThat(validator.validate(fields))
                .isEqualTo("Phone Number 1: Line number code cannot be zero");
    }

    /**
     * FR-AQ-09: the "phone not supplied" test checks the area code twice instead of checking the
     * line number, so an area code and prefix left blank make the whole number optional even when
     * a line number was typed.
     */
    @Test
    void quirkBlankAreaAndPrefixSkipTheLineNumberEdit() {
        AccountFields fields = AccountFieldsFixture.validScreen();
        fields.phone1Area = "";
        fields.phone1Prefix = "";
        fields.phone1Line = "8310";
        assertThat(validator.validate(fields)).isNull();
    }

    @Test
    void namesAddressesAndIndicatorsUseTheirOwnMessages() {
        AccountFields fields = AccountFieldsFixture.validScreen();
        fields.lastName = "";
        assertThat(validator.validate(fields)).isEqualTo("Last Name must be supplied.");

        fields = AccountFieldsFixture.validScreen();
        fields.middleName = "";
        assertThat(validator.validate(fields)).isNull();

        fields = AccountFieldsFixture.validScreen();
        fields.addrLine1 = "";
        assertThat(validator.validate(fields)).isEqualTo("Address Line 1 must be supplied.");

        fields = AccountFieldsFixture.validScreen();
        fields.eftAccountId = "005358175";
        assertThat(validator.validate(fields)).isEqualTo("EFT Account Id must be all numeric.");

        fields = AccountFieldsFixture.validScreen();
        fields.priCardHolderInd = "X";
        assertThat(validator.validate(fields)).isEqualTo("Primary Card Holder must be Y or N.");
    }
}
