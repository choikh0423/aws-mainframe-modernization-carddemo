package com.carddemo.account.validator;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** FR-AU-13..FR-AU-18: the generic edits, message for message. */
class AccountFieldEditsTest {

    @Test
    void mandatoryRejectsOnlyBlanks() {
        assertThat(AccountFieldEdits.mandatory("Address Line 1", "   ", 50))
                .isEqualTo("Address Line 1 must be supplied.");
        assertThat(AccountFieldEdits.mandatory("Address Line 1", "618 Deshaun Route", 50)).isNull();
    }

    @Test
    void yesNoAcceptsOnlyUppercaseYOrN() {
        assertThat(AccountFieldEdits.yesNo("Account Status", "")).isEqualTo("Account Status must be supplied.");
        assertThat(AccountFieldEdits.yesNo("Account Status", "0")).isEqualTo("Account Status must be supplied.");
        assertThat(AccountFieldEdits.yesNo("Account Status", "X")).isEqualTo("Account Status must be Y or N.");
        assertThat(AccountFieldEdits.yesNo("Account Status", "y")).isEqualTo("Account Status must be Y or N.");
        assertThat(AccountFieldEdits.yesNo("Account Status", "Y")).isNull();
        assertThat(AccountFieldEdits.yesNo("Account Status", "N")).isNull();
    }

    @Test
    void alphaRequiredAllowsLettersAndSpacesOnly() {
        assertThat(AccountFieldEdits.alphaRequired("First Name", "", 25))
                .isEqualTo("First Name must be supplied.");
        assertThat(AccountFieldEdits.alphaRequired("First Name", "Ann Marie", 25)).isNull();
        assertThat(AccountFieldEdits.alphaRequired("First Name", "Ann3", 25))
                .isEqualTo("First Name can have alphabets only.");
    }

    @Test
    void alphaOptionalAcceptsBlank() {
        assertThat(AccountFieldEdits.alphaOptional("Middle Name", "   ", 25)).isNull();
        assertThat(AccountFieldEdits.alphaOptional("Middle Name", "J.", 25))
                .isEqualTo("Middle Name can have alphabets only.");
    }

    @Test
    void numericRequiredChecksTheWholeMapField() {
        assertThat(AccountFieldEdits.numericRequired("Zip", "", 5)).isEqualTo("Zip must be supplied.");
        assertThat(AccountFieldEdits.numericRequired("Zip", "12546", 5)).isNull();
        assertThat(AccountFieldEdits.numericRequired("Zip", "00000", 5)).isEqualTo("Zip must not be zero.");
        // A four digit zip leaves a trailing space in the five character field, so COBOL's
        // IS NUMERIC test fails rather than a length test.
        assertThat(AccountFieldEdits.numericRequired("Zip", "1234", 5)).isEqualTo("Zip must be all numeric.");
    }

    @Test
    void signedAmountFollowsTestNumvalC() {
        assertThat(AccountFieldEdits.signedAmount("Credit Limit", "  ")).isEqualTo("Credit Limit must be supplied.");
        assertThat(AccountFieldEdits.signedAmount("Credit Limit", "+      2,020.00")).isNull();
        assertThat(AccountFieldEdits.signedAmount("Credit Limit", "-13.75")).isNull();
        assertThat(AccountFieldEdits.signedAmount("Credit Limit", "1O.00")).isEqualTo("Credit Limit is not valid");
        assertThat(AccountFieldEdits.signedAmount("Credit Limit", "1.234")).isEqualTo("Credit Limit is not valid");
    }

    @Test
    void parseAmountUnderstandsSignsBracketsAndGrouping() {
        assertThat(AccountFieldEdits.parseAmount("+      2,020.00")).isEqualByComparingTo("2020.00");
        assertThat(AccountFieldEdits.parseAmount("(13.75)")).isEqualByComparingTo("-13.75");
        assertThat(AccountFieldEdits.parseAmount("13.75CR")).isEqualByComparingTo("-13.75");
        assertThat(AccountFieldEdits.parseAmount("13.75-")).isEqualByComparingTo("-13.75");
        assertThat(AccountFieldEdits.parseAmount("$1,000")).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(AccountFieldEdits.parseAmount("abc")).isNull();
    }
}
