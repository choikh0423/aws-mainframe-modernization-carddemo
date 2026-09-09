package com.carddemo.transaction.validator;

import com.carddemo.transaction.dto.TransactionAddRequest;
import com.carddemo.transaction.exception.TransactionValidationException;
import com.carddemo.common.service.DateValidationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-A7 (required-empty), FR-A8 (numeric), FR-A9 (amount format), FR-A10 (date
 * format + real-date) and FR-A11 (confirm) coverage for {@link TransactionValidator}.
 * Pure unit test (no Spring): traces each check to COTRN02C's
 * VALIDATE-INPUT-DATA-FIELDS / PROCESS-ENTER-KEY (COTRN02C.cbl:169-436) and
 * asserts the verbatim legacy message and the "first error wins" ordering.
 */
class TransactionValidatorTest {

    private final TransactionValidator validator =
            new TransactionValidator(new DateValidationService());

    /** A fully valid CT02 request; individual tests mutate one field to fail one check. */
    private static TransactionAddRequest validRequest() {
        TransactionAddRequest r = new TransactionAddRequest();
        r.setAccountId("10000000001");
        r.setCardNumber("");
        r.setTypeCd("01");
        r.setCategoryCd("1000");
        r.setSource("POS");
        r.setDescription("GROCERY PURCHASE");
        r.setAmount("+00000123.45");
        r.setOrigDate("2023-06-01");
        r.setProcDate("2023-06-02");
        r.setMerchantId("900000001");
        r.setMerchantName("ACME SUPERMARKET");
        r.setMerchantCity("NEW YORK");
        r.setMerchantZip("10001");
        r.setConfirm("Y");
        return r;
    }

    private void expectMessage(TransactionAddRequest req, String message) {
        assertThatThrownBy(() -> validator.validateDataFields(req))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage(message);
    }

    @Test
    void validRequest_passesAllDataFieldEdits() {
        validator.validateDataFields(validRequest());
    }

    // ---- FR-A7: required-empty, in COBOL source order (COTRN02C.cbl:251-320) ----

    @Test
    void frA7_typeEmpty() {
        TransactionAddRequest r = validRequest();
        r.setTypeCd("  ");
        expectMessage(r, TransactionValidator.TYPE_EMPTY);
    }

    @Test
    void frA7_categoryEmpty() {
        TransactionAddRequest r = validRequest();
        r.setCategoryCd("");
        expectMessage(r, TransactionValidator.CATEGORY_EMPTY);
    }

    @Test
    void frA7_sourceEmpty() {
        TransactionAddRequest r = validRequest();
        r.setSource(null);
        expectMessage(r, TransactionValidator.SOURCE_EMPTY);
    }

    @Test
    void frA7_descriptionEmpty() {
        TransactionAddRequest r = validRequest();
        r.setDescription("");
        expectMessage(r, TransactionValidator.DESC_EMPTY);
    }

    @Test
    void frA7_amountEmpty() {
        TransactionAddRequest r = validRequest();
        r.setAmount("");
        expectMessage(r, TransactionValidator.AMOUNT_EMPTY);
    }

    @Test
    void frA7_origDateEmpty() {
        TransactionAddRequest r = validRequest();
        r.setOrigDate("");
        expectMessage(r, TransactionValidator.ORIG_DATE_EMPTY);
    }

    @Test
    void frA7_procDateEmpty() {
        TransactionAddRequest r = validRequest();
        r.setProcDate("");
        expectMessage(r, TransactionValidator.PROC_DATE_EMPTY);
    }

    @Test
    void frA7_merchantIdEmpty() {
        TransactionAddRequest r = validRequest();
        r.setMerchantId("");
        expectMessage(r, TransactionValidator.MERCHANT_ID_EMPTY);
    }

    @Test
    void frA7_merchantNameEmpty() {
        TransactionAddRequest r = validRequest();
        r.setMerchantName("");
        expectMessage(r, TransactionValidator.MERCHANT_NAME_EMPTY);
    }

    @Test
    void frA7_merchantCityEmpty() {
        TransactionAddRequest r = validRequest();
        r.setMerchantCity("");
        expectMessage(r, TransactionValidator.MERCHANT_CITY_EMPTY);
    }

    @Test
    void frA7_merchantZipEmpty() {
        TransactionAddRequest r = validRequest();
        r.setMerchantZip("");
        expectMessage(r, TransactionValidator.MERCHANT_ZIP_EMPTY);
    }

    // ---- FR-A8: numeric checks (COTRN02C.cbl:322-337, 430-436) ----

    @Test
    void frA8_typeNotNumeric() {
        TransactionAddRequest r = validRequest();
        r.setTypeCd("AB");
        expectMessage(r, TransactionValidator.TYPE_NUMERIC);
    }

    @Test
    void frA8_categoryNotNumeric() {
        TransactionAddRequest r = validRequest();
        r.setCategoryCd("10X0");
        expectMessage(r, TransactionValidator.CATEGORY_NUMERIC);
    }

    @Test
    void frA8_merchantIdNotNumeric() {
        TransactionAddRequest r = validRequest();
        r.setMerchantId("9000000A1");
        expectMessage(r, TransactionValidator.MERCHANT_ID_NUMERIC);
    }

    // ---- FR-A9: amount format -99999999.99 (COTRN02C.cbl:339-351) ----

    @Test
    void frA9_amountWrongFormat() {
        TransactionAddRequest r = validRequest();
        r.setAmount("123.45");
        expectMessage(r, TransactionValidator.AMOUNT_FORMAT);
    }

    @Test
    void frA9_amountMissingSign() {
        TransactionAddRequest r = validRequest();
        r.setAmount("00000123.45");
        expectMessage(r, TransactionValidator.AMOUNT_FORMAT);
    }

    @Test
    void frA9_negativeAmountAccepted() {
        TransactionAddRequest r = validRequest();
        r.setAmount("-00000025.00");
        validator.validateDataFields(r);
    }

    // ---- FR-A10: date format + real-date (COTRN02C.cbl:353-427) ----

    @Test
    void frA10_origDateWrongShape() {
        TransactionAddRequest r = validRequest();
        r.setOrigDate("2023/06/01");
        expectMessage(r, TransactionValidator.ORIG_DATE_FORMAT);
    }

    @Test
    void frA10_procDateWrongShape() {
        TransactionAddRequest r = validRequest();
        r.setProcDate("06-01-2023");
        expectMessage(r, TransactionValidator.PROC_DATE_FORMAT);
    }

    @Test
    void frA10_origDateNotARealDate() {
        TransactionAddRequest r = validRequest();
        r.setOrigDate("2023-02-30");
        expectMessage(r, TransactionValidator.ORIG_DATE_INVALID);
    }

    @Test
    void frA10_procDateNotARealDate() {
        TransactionAddRequest r = validRequest();
        r.setProcDate("2023-13-01");
        expectMessage(r, TransactionValidator.PROC_DATE_INVALID);
    }

    // ---- FR-A11: confirm gate (COTRN02C.cbl:169-188) ----

    @Test
    void frA11_confirmY_returnsTrue() {
        assertThat(validator.validateConfirm("Y")).isTrue();
        assertThat(validator.validateConfirm("y")).isTrue();
    }

    @Test
    void frA11_confirmBlank_promptsToConfirm() {
        assertThatThrownBy(() -> validator.validateConfirm(""))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage(TransactionValidator.CONFIRM_REQUIRED);
        assertThatThrownBy(() -> validator.validateConfirm(null))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage(TransactionValidator.CONFIRM_REQUIRED);
    }

    @Test
    void frA11_confirmN_promptsToConfirm() {
        assertThatThrownBy(() -> validator.validateConfirm("N"))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage(TransactionValidator.CONFIRM_REQUIRED);
    }

    @Test
    void frA11_confirmInvalidValue() {
        assertThatThrownBy(() -> validator.validateConfirm("X"))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage(TransactionValidator.CONFIRM_INVALID);
    }

    // ---- ordering: first failure wins (empty precedes numeric) ----

    @Test
    void firstErrorWins_emptyBeforeNumeric() {
        TransactionAddRequest r = validRequest();
        r.setTypeCd("");     // empty check for Type CD comes first...
        r.setCategoryCd("X"); // ...even though Category CD is also invalid.
        expectMessage(r, TransactionValidator.TYPE_EMPTY);
    }
}
