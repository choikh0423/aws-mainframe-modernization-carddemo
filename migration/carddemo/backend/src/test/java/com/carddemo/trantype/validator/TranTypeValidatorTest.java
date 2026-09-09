package com.carddemo.trantype.validator;

import com.carddemo.trantype.message.TranTypeMessages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shared field edits: {@code 1240-EDIT-ALPHANUM-REQD} (COTRTLIC.cbl:1181),
 * {@code 1320-EDIT-NUM-REQD} (COTRTUPC.cbl:907) and the {@code PIC 9(2)} move
 * of {@code 1210-EDIT-TRANTYPE} (COTRTUPC.cbl:834).
 *
 * <p>Covers FR-L03, FR-L15, FR-L16, FR-U04..FR-U07, FR-U11, FR-U12 and FR-U24.
 */
class TranTypeValidatorTest {

    private final TranTypeValidator validator = new TranTypeValidator();

    @Test
    void treatsStarAndSpacesAsNotSupplied() {
        assertThat(validator.normalizeInput("*")).isEmpty();
        assertThat(validator.normalizeInput("   ")).isEmpty();
        assertThat(validator.normalizeInput(null)).isEmpty();
        assertThat(validator.normalizeInput("  01 ")).isEqualTo("01");
        assertThat(validator.isBlank("*")).isTrue();
        assertThat(validator.isBlank("01")).isFalse();
    }

    @Test
    void requiresTheTransactionTypeCode() {
        assertThat(validator.validateTypeCode("")).isEqualTo(TranTypeMessages.TYPE_CODE_REQUIRED);
        assertThat(validator.validateTypeCode("*")).isEqualTo(TranTypeMessages.TYPE_CODE_REQUIRED);
    }

    @Test
    void rejectsANonNumericTransactionTypeCode() {
        assertThat(validator.validateTypeCode("A1")).isEqualTo(TranTypeMessages.TYPE_CODE_NOT_NUMERIC);
        assertThat(validator.validateTypeCode("1 ")).isNull();
    }

    @Test
    void rejectsAZeroTransactionTypeCode() {
        assertThat(validator.validateTypeCode("00")).isEqualTo(TranTypeMessages.TYPE_CODE_ZERO);
        assertThat(validator.validateTypeCode("0")).isEqualTo(TranTypeMessages.TYPE_CODE_ZERO);
    }

    @Test
    void acceptsAValidTransactionTypeCode() {
        assertThat(validator.validateTypeCode("07")).isNull();
    }

    @Test
    void normalisesTheTypeCodeThroughATwoDigitNumericField() {
        assertThat(validator.normalizeTypeCode("7")).isEqualTo("07");
        assertThat(validator.normalizeTypeCode("07")).isEqualTo("07");
        assertThat(validator.normalizeTypeCode("123")).isEqualTo("23");
        assertThat(validator.normalizeTypeCode("AB")).isEqualTo("AB");
    }

    @Test
    void requiresTheDescription() {
        assertThat(validator.validateDescription("  "))
                .isEqualTo(TranTypeMessages.DESCRIPTION_REQUIRED);
    }

    @Test
    void allowsOnlyLettersDigitsAndSpacesInTheDescription() {
        assertThat(validator.validateDescription("Cash Advance 2")).isNull();
        assertThat(validator.validateDescription("Cash-Advance"))
                .isEqualTo(TranTypeMessages.DESCRIPTION_NOT_ALPHANUM);
    }

    @Test
    void appliesTheCobolNumericTestCharacterByCharacter() {
        assertThat(validator.isNumeric("00")).isTrue();
        assertThat(validator.isNumeric("0A")).isFalse();
        assertThat(validator.isNumeric("")).isFalse();
        assertThat(validator.isNumeric(null)).isFalse();
    }
}
