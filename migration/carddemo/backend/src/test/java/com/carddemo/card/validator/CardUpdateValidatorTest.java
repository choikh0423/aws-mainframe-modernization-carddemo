package com.carddemo.card.validator;

import com.carddemo.card.exception.CardValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-U9..FR-U13 coverage for the CCUP field edits
 * (COCRDUPC.cbl:806-943: 1230-EDIT-NAME, 1240-EDIT-CARDSTATUS,
 * 1250-EDIT-EXPIRY-MON, 1260-EDIT-EXPIRY-YEAR).
 */
class CardUpdateValidatorTest {

    private final CardUpdateValidator validator = new CardUpdateValidator();

    @Test
    void frU9_nameMustBeSupplied() {
        assertThatThrownBy(() -> validator.validateName("   "))
                .isInstanceOf(CardValidationException.class)
                .hasMessage("Card name not provided");
    }

    @Test
    void frU10_nameIsAlphabetsAndSpacesOnly() {
        assertThatCode(() -> validator.validateName("JOHN Q PUBLIC")).doesNotThrowAnyException();
        // Punctuation the seeded data itself contains (O'Connell) is rejected on input.
        assertThatThrownBy(() -> validator.validateName("LUCIOUS O'CONNELL"))
                .hasMessage("Card name can only contain alphabets and spaces");
        assertThatThrownBy(() -> validator.validateName("JOHN 2ND"))
                .hasMessage("Card name can only contain alphabets and spaces");
    }

    @Test
    void frU11_statusIsUpperCaseYorN() {
        assertThatCode(() -> validator.validateStatus("Y")).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateStatus("N")).doesNotThrowAnyException();
        // 88 FLG-YES-NO-VALID lists 'Y' and 'N' only: lower case fails.
        assertThatThrownBy(() -> validator.validateStatus("y"))
                .hasMessage("Card Active Status must be Y or N");
        assertThatThrownBy(() -> validator.validateStatus(""))
                .hasMessage("Card Active Status must be Y or N");
    }

    @Test
    void frU12_expiryMonthIs1To12() {
        assertThatCode(() -> validator.validateExpiryMonth("01")).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateExpiryMonth("12")).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateExpiryMonth("00"))
                .hasMessage("Card expiry month must be between 1 and 12");
        assertThatThrownBy(() -> validator.validateExpiryMonth("13"))
                .hasMessage("Card expiry month must be between 1 and 12");
        assertThatThrownBy(() -> validator.validateExpiryMonth("1A"))
                .hasMessage("Card expiry month must be between 1 and 12");
    }

    @Test
    void frU13_expiryYearIs1950To2099() {
        assertThatCode(() -> validator.validateExpiryYear("1950")).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateExpiryYear("2099")).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateExpiryYear("1949"))
                .hasMessage("Invalid card expiry year");
        assertThatThrownBy(() -> validator.validateExpiryYear("2100"))
                .hasMessage("Invalid card expiry year");
    }

    @Test
    void frU9_frU13_editsRunInMapOrderSoTheFirstFailureIsReported() {
        // A bad name and a bad month together: the name message reaches ERRMSG.
        assertThatThrownBy(() -> validator.validate("JOHN 2ND", "Y", "13", "2025"))
                .hasMessage("Card name can only contain alphabets and spaces");
    }
}
