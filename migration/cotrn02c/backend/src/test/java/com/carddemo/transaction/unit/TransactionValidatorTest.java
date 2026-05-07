package com.carddemo.transaction.unit;

import com.carddemo.transaction.dto.AddTransactionRequest;
import com.carddemo.transaction.entity.CardXrefRecord;
import com.carddemo.transaction.exception.ValidationException;
import com.carddemo.transaction.repository.CardXrefRepository;
import com.carddemo.transaction.validation.TransactionValidator;
import com.carddemo.transaction.validation.TransactionValidator.KeyValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TransactionValidator — all 18 validation rules.
 * Each test verifies 1:1 parity with the COBOL EVALUATE TRUE logic,
 * including exact error message text and first-error-only behavior.
 */
@ExtendWith(MockitoExtension.class)
class TransactionValidatorTest {

    @Mock
    private CardXrefRepository cardXrefRepository;

    private TransactionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TransactionValidator(cardXrefRepository);
    }

    private CardXrefRecord makeXref(String cardNum, long custId, long acctId) {
        CardXrefRecord rec = new CardXrefRecord();
        rec.setXrefCardNum(cardNum);
        rec.setXrefCustId(custId);
        rec.setXrefAcctId(acctId);
        return rec;
    }

    // ========================================================================
    // Phase 1: VALIDATE-INPUT-KEY-FIELDS (COTRN02C lines 193-230)
    // ========================================================================
    @Nested
    @DisplayName("Phase 1: Key Field Validation")
    class KeyFieldValidation {

        @Test
        @DisplayName("TC-K01: Neither acctId nor cardNum entered")
        void neitherEntered() {
            AddTransactionRequest req = new AddTransactionRequest();
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateKeyFields(req));
            assertEquals("Account or Card Number must be entered...", ex.getMessage());
            assertEquals("acctId", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-K02: acctId non-numeric")
        void acctIdNonNumeric() {
            AddTransactionRequest req = new AddTransactionRequest();
            req.setAcctId("ABC");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateKeyFields(req));
            assertEquals("Account ID must be Numeric...", ex.getMessage());
            assertEquals("acctId", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-K03: acctId not found in XREF")
        void acctIdNotFound() {
            AddTransactionRequest req = new AddTransactionRequest();
            req.setAcctId("99999999999");
            when(cardXrefRepository.findByXrefAcctId(99999999999L)).thenReturn(Optional.empty());
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateKeyFields(req));
            assertEquals("Account ID NOT found...", ex.getMessage());
            assertEquals("acctId", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-K04: cardNum non-numeric")
        void cardNumNonNumeric() {
            AddTransactionRequest req = new AddTransactionRequest();
            req.setCardNum("ABCDEFGH");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateKeyFields(req));
            assertEquals("Card Number must be Numeric...", ex.getMessage());
            assertEquals("cardNum", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-K05: cardNum not found in XREF")
        void cardNumNotFound() {
            AddTransactionRequest req = new AddTransactionRequest();
            req.setCardNum("9999999999999999");
            when(cardXrefRepository.findById("9999999999999999")).thenReturn(Optional.empty());
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateKeyFields(req));
            assertEquals("Card Number NOT found...", ex.getMessage());
            assertEquals("cardNum", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-K06: Both entered — acctId takes precedence")
        void bothEnteredAcctIdPrecedence() {
            AddTransactionRequest req = new AddTransactionRequest();
            req.setAcctId("1");
            req.setCardNum("4111111111111111");
            CardXrefRecord xref = makeXref("4111111111111111", 100000001, 1);
            when(cardXrefRepository.findByXrefAcctId(1L)).thenReturn(Optional.of(xref));
            KeyValidationResult result = validator.validateKeyFields(req);
            assertEquals("4111111111111111", result.getResolvedCardNum());
            assertEquals("00000000001", result.getResolvedAcctId());
        }

        @Test
        @DisplayName("TC-K07: acctId valid — resolves card number")
        void acctIdValid() {
            AddTransactionRequest req = new AddTransactionRequest();
            req.setAcctId("1");
            CardXrefRecord xref = makeXref("4111111111111111", 100000001, 1);
            when(cardXrefRepository.findByXrefAcctId(1L)).thenReturn(Optional.of(xref));
            KeyValidationResult result = validator.validateKeyFields(req);
            assertEquals("4111111111111111", result.getResolvedCardNum());
            assertEquals("00000000001", result.getResolvedAcctId());
        }

        @Test
        @DisplayName("TC-K08: cardNum valid — resolves account ID")
        void cardNumValid() {
            AddTransactionRequest req = new AddTransactionRequest();
            req.setCardNum("4111111111111111");
            CardXrefRecord xref = makeXref("4111111111111111", 100000001, 1);
            when(cardXrefRepository.findById("4111111111111111")).thenReturn(Optional.of(xref));
            KeyValidationResult result = validator.validateKeyFields(req);
            assertEquals("4111111111111111", result.getResolvedCardNum());
            assertEquals("00000000001", result.getResolvedAcctId());
        }
    }

    // ========================================================================
    // Phase 2: Empty Field Checks (COTRN02C lines 251-320)
    // ========================================================================
    @Nested
    @DisplayName("Phase 2: Empty Field Checks")
    class EmptyFieldChecks {

        private AddTransactionRequest makeFullRequest() {
            AddTransactionRequest req = new AddTransactionRequest();
            req.setTypeCd("01");
            req.setCatCd("5001");
            req.setSource("MANUAL");
            req.setDescription("Test transaction");
            req.setAmount("+00000250.00");
            req.setOrigDate("2024-01-15");
            req.setProcDate("2024-01-16");
            req.setMerchantId("123456789");
            req.setMerchantName("Test Merchant");
            req.setMerchantCity("Chicago");
            req.setMerchantZip("60601");
            return req;
        }

        @Test
        @DisplayName("TC-E01: typeCd empty")
        void typeCdEmpty() {
            AddTransactionRequest req = makeFullRequest();
            req.setTypeCd("");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Type CD can NOT be empty...", ex.getMessage());
            assertEquals("typeCd", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-E02: catCd empty")
        void catCdEmpty() {
            AddTransactionRequest req = makeFullRequest();
            req.setCatCd("");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Category CD can NOT be empty...", ex.getMessage());
            assertEquals("catCd", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-E03: source empty")
        void sourceEmpty() {
            AddTransactionRequest req = makeFullRequest();
            req.setSource("");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Source can NOT be empty...", ex.getMessage());
            assertEquals("source", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-E04: description empty")
        void descriptionEmpty() {
            AddTransactionRequest req = makeFullRequest();
            req.setDescription("");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Description can NOT be empty...", ex.getMessage());
            assertEquals("description", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-E05: amount empty")
        void amountEmpty() {
            AddTransactionRequest req = makeFullRequest();
            req.setAmount("");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Amount can NOT be empty...", ex.getMessage());
            assertEquals("amount", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-E06: origDate empty")
        void origDateEmpty() {
            AddTransactionRequest req = makeFullRequest();
            req.setOrigDate("");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Orig Date can NOT be empty...", ex.getMessage());
            assertEquals("origDate", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-E07: procDate empty")
        void procDateEmpty() {
            AddTransactionRequest req = makeFullRequest();
            req.setProcDate("");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Proc Date can NOT be empty...", ex.getMessage());
            assertEquals("procDate", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-E08: merchantId empty")
        void merchantIdEmpty() {
            AddTransactionRequest req = makeFullRequest();
            req.setMerchantId("");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Merchant ID can NOT be empty...", ex.getMessage());
            assertEquals("merchantId", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-E09: merchantName empty")
        void merchantNameEmpty() {
            AddTransactionRequest req = makeFullRequest();
            req.setMerchantName("");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Merchant Name can NOT be empty...", ex.getMessage());
            assertEquals("merchantName", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-E10: merchantCity empty")
        void merchantCityEmpty() {
            AddTransactionRequest req = makeFullRequest();
            req.setMerchantCity("");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Merchant City can NOT be empty...", ex.getMessage());
            assertEquals("merchantCity", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-E11: merchantZip empty")
        void merchantZipEmpty() {
            AddTransactionRequest req = makeFullRequest();
            req.setMerchantZip("");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Merchant Zip can NOT be empty...", ex.getMessage());
            assertEquals("merchantZip", ex.getErrorField());
        }
    }

    // ========================================================================
    // Phase 3: Type/Format Validation (COTRN02C lines 322-386)
    // ========================================================================
    @Nested
    @DisplayName("Phase 3: Type/Format Validation")
    class TypeFormatValidation {

        private AddTransactionRequest makeFullRequest() {
            AddTransactionRequest req = new AddTransactionRequest();
            req.setTypeCd("01");
            req.setCatCd("5001");
            req.setSource("MANUAL");
            req.setDescription("Test transaction");
            req.setAmount("+00000250.00");
            req.setOrigDate("2024-01-15");
            req.setProcDate("2024-01-16");
            req.setMerchantId("123456789");
            req.setMerchantName("Test Merchant");
            req.setMerchantCity("Chicago");
            req.setMerchantZip("60601");
            return req;
        }

        @Test
        @DisplayName("TC-F01: typeCd non-numeric")
        void typeCdNonNumeric() {
            AddTransactionRequest req = makeFullRequest();
            req.setTypeCd("AB");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Type CD must be Numeric...", ex.getMessage());
            assertEquals("typeCd", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-F02: catCd non-numeric")
        void catCdNonNumeric() {
            AddTransactionRequest req = makeFullRequest();
            req.setCatCd("ABCD");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Category CD must be Numeric...", ex.getMessage());
            assertEquals("catCd", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-F03: amount bad format (no sign)")
        void amountBadFormatNoSign() {
            AddTransactionRequest req = makeFullRequest();
            req.setAmount("00000250.00");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Amount should be in format -99999999.99", ex.getMessage());
            assertEquals("amount", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-F04: amount bad format (too short)")
        void amountBadFormatTooShort() {
            AddTransactionRequest req = makeFullRequest();
            req.setAmount("+250.00");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Amount should be in format -99999999.99", ex.getMessage());
            assertEquals("amount", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-F05: origDate bad format")
        void origDateBadFormat() {
            AddTransactionRequest req = makeFullRequest();
            req.setOrigDate("01-15-2024");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Orig Date should be in format YYYY-MM-DD", ex.getMessage());
            assertEquals("origDate", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-F06: procDate bad format")
        void procDateBadFormat() {
            AddTransactionRequest req = makeFullRequest();
            req.setProcDate("2024/01/16");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Proc Date should be in format YYYY-MM-DD", ex.getMessage());
            assertEquals("procDate", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-F07: merchantId non-numeric")
        void merchantIdNonNumeric() {
            AddTransactionRequest req = makeFullRequest();
            req.setMerchantId("ABC123456");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Merchant ID must be Numeric...", ex.getMessage());
            assertEquals("merchantId", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-F08: valid amount format accepted (+99999999.99)")
        void validAmountFormat() {
            AddTransactionRequest req = makeFullRequest();
            req.setAmount("+00000250.00");
            String normalized = validator.validateDataFields(req);
            assertNotNull(normalized);
            assertEquals("+00000250.00", normalized);
        }

        @Test
        @DisplayName("TC-F09: negative amount format accepted (-99999999.99)")
        void negativeAmountFormat() {
            AddTransactionRequest req = makeFullRequest();
            req.setAmount("-00000100.50");
            String normalized = validator.validateDataFields(req);
            assertNotNull(normalized);
            assertEquals("-00000100.50", normalized);
        }
    }

    // ========================================================================
    // Phase 4: Date Validity (replaces CSUTLDTC / CEEDAYS)
    // ========================================================================
    @Nested
    @DisplayName("Phase 4: Date Validity")
    class DateValidity {

        private AddTransactionRequest makeFullRequest() {
            AddTransactionRequest req = new AddTransactionRequest();
            req.setTypeCd("01");
            req.setCatCd("5001");
            req.setSource("MANUAL");
            req.setDescription("Test transaction");
            req.setAmount("+00000250.00");
            req.setOrigDate("2024-01-15");
            req.setProcDate("2024-01-16");
            req.setMerchantId("123456789");
            req.setMerchantName("Test Merchant");
            req.setMerchantCity("Chicago");
            req.setMerchantZip("60601");
            return req;
        }

        @Test
        @DisplayName("TC-D01: origDate invalid (Feb 30)")
        void origDateInvalidFeb30() {
            AddTransactionRequest req = makeFullRequest();
            req.setOrigDate("2024-02-30");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Orig Date - Not a valid date...", ex.getMessage());
            assertEquals("origDate", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-D02: procDate invalid (Feb 29 in non-leap year)")
        void procDateInvalidNonLeap() {
            AddTransactionRequest req = makeFullRequest();
            req.setProcDate("2023-02-29");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Proc Date - Not a valid date...", ex.getMessage());
            assertEquals("procDate", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-D03: origDate invalid (month 13)")
        void origDateInvalidMonth13() {
            AddTransactionRequest req = makeFullRequest();
            req.setOrigDate("2024-13-01");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Orig Date - Not a valid date...", ex.getMessage());
            assertEquals("origDate", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-D04: Feb 29 in leap year is VALID")
        void leapYearFeb29Valid() {
            AddTransactionRequest req = makeFullRequest();
            req.setOrigDate("2024-02-29");
            req.setProcDate("2024-03-01");
            String normalized = validator.validateDataFields(req);
            assertNotNull(normalized);
        }
    }

    // ========================================================================
    // Phase 5: Confirmation Validation (COTRN02C lines 169-188)
    // ========================================================================
    @Nested
    @DisplayName("Phase 5: Confirmation")
    class ConfirmationValidation {

        @Test
        @DisplayName("TC-C01: confirm = Y (proceed)")
        void confirmY() {
            assertDoesNotThrow(() -> validator.validateConfirmation("Y"));
        }

        @Test
        @DisplayName("TC-C02: confirm = y (lowercase, proceed)")
        void confirmLowerY() {
            assertDoesNotThrow(() -> validator.validateConfirmation("y"));
        }

        @Test
        @DisplayName("TC-C03: confirm = N (ask again)")
        void confirmN() {
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateConfirmation("N"));
            assertEquals("Confirm to add this transaction...", ex.getMessage());
            assertEquals("confirm", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-C04: confirm = blank (ask again)")
        void confirmBlank() {
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateConfirmation(""));
            assertEquals("Confirm to add this transaction...", ex.getMessage());
            assertEquals("confirm", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-C05: confirm = null (ask again)")
        void confirmNull() {
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateConfirmation(null));
            assertEquals("Confirm to add this transaction...", ex.getMessage());
            assertEquals("confirm", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-C06: confirm = X (invalid)")
        void confirmInvalid() {
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateConfirmation("X"));
            assertEquals("Invalid value. Valid values are (Y/N)...", ex.getMessage());
            assertEquals("confirm", ex.getErrorField());
        }
    }

    // ========================================================================
    // Short-circuit behavior: verify only first error is returned
    // ========================================================================
    @Nested
    @DisplayName("Short-Circuit: First-Error-Only Behavior")
    class ShortCircuit {

        @Test
        @DisplayName("TC-SC01: Multiple empty fields — only first error returned")
        void multipleEmptyOnlyFirstError() {
            AddTransactionRequest req = new AddTransactionRequest();
            // All fields empty — should get typeCd error first
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Type CD can NOT be empty...", ex.getMessage());
            assertEquals("typeCd", ex.getErrorField());
        }

        @Test
        @DisplayName("TC-SC02: typeCd filled but catCd empty — catCd error")
        void typeCdFilledCatCdEmpty() {
            AddTransactionRequest req = new AddTransactionRequest();
            req.setTypeCd("01");
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validateDataFields(req));
            assertEquals("Category CD can NOT be empty...", ex.getMessage());
            assertEquals("catCd", ex.getErrorField());
        }
    }
}
