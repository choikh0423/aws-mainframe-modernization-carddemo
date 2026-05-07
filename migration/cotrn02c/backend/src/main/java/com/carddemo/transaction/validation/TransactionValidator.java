package com.carddemo.transaction.validation;

import com.carddemo.transaction.dto.AddTransactionRequest;
import com.carddemo.transaction.entity.CardXrefRecord;
import com.carddemo.transaction.exception.ValidationException;
import com.carddemo.transaction.repository.CardXrefRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;

/**
 * Implements all 18+ validation rules from COTRN02C, preserving:
 *   1. Exact validation order (COBOL paragraph sequence)
 *   2. First-error-only short-circuit (SEND-TRNADD-SCREEN contains RETURN)
 *   3. Exact error message text from the COBOL source
 *   4. Exact field cursor positioning (errorField maps to BMS CURSOR)
 *
 * COBOL paragraphs replicated:
 *   - VALIDATE-INPUT-KEY-FIELDS  (lines 193-230)
 *   - VALIDATE-INPUT-DATA-FIELDS (lines 235-437)
 */
@Component
public class TransactionValidator {

    private static final DateTimeFormatter STRICT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd")
                    .withResolverStyle(ResolverStyle.STRICT);

    private final CardXrefRepository cardXrefRepository;

    public TransactionValidator(CardXrefRepository cardXrefRepository) {
        this.cardXrefRepository = cardXrefRepository;
    }

    /**
     * Result holder for key field validation. On success, carries the
     * resolved card number and account ID from the XREF lookup.
     */
    public static class KeyValidationResult {
        private final String resolvedCardNum;
        private final String resolvedAcctId;

        public KeyValidationResult(String resolvedCardNum, String resolvedAcctId) {
            this.resolvedCardNum = resolvedCardNum;
            this.resolvedAcctId = resolvedAcctId;
        }

        public String getResolvedCardNum() { return resolvedCardNum; }
        public String getResolvedAcctId() { return resolvedAcctId; }
    }

    /**
     * VALIDATE-INPUT-KEY-FIELDS (COTRN02C.cbl lines 193-230).
     *
     * COBOL EVALUATE TRUE:
     *   WHEN acctId entered -> check numeric, READ CXACAIX, get card num
     *   WHEN cardNum entered -> check numeric, READ CCXREF, get acct id
     *   WHEN OTHER -> "Account or Card Number must be entered..."
     *
     * Account ID takes precedence when both are entered (first WHEN match).
     *
     * @throws ValidationException on first error (short-circuit)
     */
    public KeyValidationResult validateKeyFields(AddTransactionRequest req) {
        String acctId = req.getAcctId();
        String cardNum = req.getCardNum();

        boolean acctIdEntered = acctId != null && !acctId.isBlank();
        boolean cardNumEntered = cardNum != null && !cardNum.isBlank();

        if (acctIdEntered) {
            if (!acctId.chars().allMatch(Character::isDigit)) {
                throw new ValidationException("Account ID must be Numeric...", "acctId");
            }
            long acctIdNum = Long.parseLong(acctId);
            Optional<CardXrefRecord> xref = cardXrefRepository.findByXrefAcctId(acctIdNum);
            if (xref.isEmpty()) {
                throw new ValidationException("Account ID NOT found...", "acctId");
            }
            String resolvedCard = xref.get().getXrefCardNum();
            String resolvedAcct = String.format("%011d", acctIdNum);
            return new KeyValidationResult(resolvedCard, resolvedAcct);

        } else if (cardNumEntered) {
            if (!cardNum.chars().allMatch(Character::isDigit)) {
                throw new ValidationException("Card Number must be Numeric...", "cardNum");
            }
            long cardNumN = Long.parseLong(cardNum);
            String paddedCardNum = String.format("%016d", cardNumN);
            Optional<CardXrefRecord> xref = cardXrefRepository.findById(paddedCardNum);
            if (xref.isEmpty()) {
                throw new ValidationException("Card Number NOT found...", "cardNum");
            }
            String resolvedAcct = String.format("%011d", xref.get().getXrefAcctId());
            return new KeyValidationResult(paddedCardNum, resolvedAcct);

        } else {
            throw new ValidationException(
                    "Account or Card Number must be entered...", "acctId");
        }
    }

    /**
     * VALIDATE-INPUT-DATA-FIELDS (COTRN02C.cbl lines 235-437).
     *
     * Implements all validation phases in exact COBOL order:
     *   Phase 2: Empty field checks (11 fields, sequential)
     *   Phase 3: Type/format validation (typeCd, catCd, amount, dates)
     *   Phase 4: Date validity via java.time (replaces CSUTLDTC/CEEDAYS)
     *   Phase 5: Merchant ID numeric check
     *
     * Each check short-circuits on first error (throws ValidationException).
     *
     * @return normalized amount string in COBOL display format (+99999999.99)
     * @throws ValidationException on first error
     */
    public String validateDataFields(AddTransactionRequest req) {

        // Phase 2: Empty field checks (COBOL lines 251-320)
        // Order matches the EVALUATE TRUE WHEN sequence exactly
        if (isBlank(req.getTypeCd())) {
            throw new ValidationException("Type CD can NOT be empty...", "typeCd");
        }
        if (isBlank(req.getCatCd())) {
            throw new ValidationException("Category CD can NOT be empty...", "catCd");
        }
        if (isBlank(req.getSource())) {
            throw new ValidationException("Source can NOT be empty...", "source");
        }
        if (isBlank(req.getDescription())) {
            throw new ValidationException("Description can NOT be empty...", "description");
        }
        if (isBlank(req.getAmount())) {
            throw new ValidationException("Amount can NOT be empty...", "amount");
        }
        if (isBlank(req.getOrigDate())) {
            throw new ValidationException("Orig Date can NOT be empty...", "origDate");
        }
        if (isBlank(req.getProcDate())) {
            throw new ValidationException("Proc Date can NOT be empty...", "procDate");
        }
        if (isBlank(req.getMerchantId())) {
            throw new ValidationException("Merchant ID can NOT be empty...", "merchantId");
        }
        if (isBlank(req.getMerchantName())) {
            throw new ValidationException("Merchant Name can NOT be empty...", "merchantName");
        }
        if (isBlank(req.getMerchantCity())) {
            throw new ValidationException("Merchant City can NOT be empty...", "merchantCity");
        }
        if (isBlank(req.getMerchantZip())) {
            throw new ValidationException("Merchant Zip can NOT be empty...", "merchantZip");
        }

        // Phase 3a: Type CD must be numeric (COBOL lines 322-337)
        if (!req.getTypeCd().chars().allMatch(Character::isDigit)) {
            throw new ValidationException("Type CD must be Numeric...", "typeCd");
        }

        // Phase 3b: Category CD must be numeric
        if (!req.getCatCd().chars().allMatch(Character::isDigit)) {
            throw new ValidationException("Category CD must be Numeric...", "catCd");
        }

        // Phase 3c: Amount format validation (COBOL lines 339-351)
        // COBOL checks: pos 1 = +/-, pos 2-9 = digits, pos 10 = '.', pos 11-12 = digits
        String amt = req.getAmount();
        if (!isValidAmountFormat(amt)) {
            throw new ValidationException(
                    "Amount should be in format -99999999.99", "amount");
        }

        // Phase 3d: Orig Date format YYYY-MM-DD (COBOL lines 353-366)
        if (!isValidDateFormat(req.getOrigDate())) {
            throw new ValidationException(
                    "Orig Date should be in format YYYY-MM-DD", "origDate");
        }

        // Phase 3e: Proc Date format YYYY-MM-DD (COBOL lines 368-381)
        if (!isValidDateFormat(req.getProcDate())) {
            throw new ValidationException(
                    "Proc Date should be in format YYYY-MM-DD", "procDate");
        }

        // Phase 3f: Normalize amount via NUMVAL-C equivalent (COBOL lines 383-386)
        String normalizedAmount = normalizeAmount(amt);

        // Phase 4a: Orig Date validity via CSUTLDTC/CEEDAYS replacement (COBOL lines 389-407)
        if (!isValidDate(req.getOrigDate())) {
            throw new ValidationException(
                    "Orig Date - Not a valid date...", "origDate");
        }

        // Phase 4b: Proc Date validity (COBOL lines 409-427)
        if (!isValidDate(req.getProcDate())) {
            throw new ValidationException(
                    "Proc Date - Not a valid date...", "procDate");
        }

        // Phase 5: Merchant ID must be numeric (COBOL lines 430-436)
        if (!req.getMerchantId().chars().allMatch(Character::isDigit)) {
            throw new ValidationException(
                    "Merchant ID must be Numeric...", "merchantId");
        }

        return normalizedAmount;
    }

    /**
     * Validate confirmation field (COBOL PROCESS-ENTER-KEY lines 169-188).
     *
     *   'Y'/'y'         -> proceed to ADD-TRANSACTION
     *   'N'/'n'/spaces  -> "Confirm to add this transaction..."
     *   Other           -> "Invalid value. Valid values are (Y/N)..."
     *
     * @throws ValidationException for N/spaces/other values
     */
    public void validateConfirmation(String confirm) {
        if (confirm == null || confirm.isBlank()) {
            throw new ValidationException(
                    "Confirm to add this transaction...", "confirm");
        }
        String upper = confirm.toUpperCase();
        if ("Y".equals(upper)) {
            return; // proceed
        }
        if ("N".equals(upper)) {
            throw new ValidationException(
                    "Confirm to add this transaction...", "confirm");
        }
        throw new ValidationException(
                "Invalid value. Valid values are (Y/N)...", "confirm");
    }

    /**
     * COBOL amount format check (lines 339-351):
     *   Position 1:    must be '+' or '-'
     *   Position 2-9:  must be digits (8 digits)
     *   Position 10:   must be '.'
     *   Position 11-12: must be digits (2 digits)
     *
     * Total length: 12 characters exactly.
     */
    private boolean isValidAmountFormat(String amt) {
        if (amt == null || amt.length() != 12) {
            return false;
        }
        char sign = amt.charAt(0);
        if (sign != '+' && sign != '-') {
            return false;
        }
        for (int i = 1; i <= 8; i++) {
            if (!Character.isDigit(amt.charAt(i))) {
                return false;
            }
        }
        if (amt.charAt(9) != '.') {
            return false;
        }
        if (!Character.isDigit(amt.charAt(10)) || !Character.isDigit(amt.charAt(11))) {
            return false;
        }
        return true;
    }

    /**
     * COBOL date format check (lines 353-366 / 368-381):
     *   Position 1-4: digits (YYYY)
     *   Position 5:   '-'
     *   Position 6-7: digits (MM)
     *   Position 8:   '-'
     *   Position 9-10: digits (DD)
     */
    private boolean isValidDateFormat(String date) {
        if (date == null || date.length() != 10) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            if (!Character.isDigit(date.charAt(i))) return false;
        }
        if (date.charAt(4) != '-') return false;
        if (!Character.isDigit(date.charAt(5)) || !Character.isDigit(date.charAt(6))) return false;
        if (date.charAt(7) != '-') return false;
        if (!Character.isDigit(date.charAt(8)) || !Character.isDigit(date.charAt(9))) return false;
        return true;
    }

    /**
     * Replaces CSUTLDTC -> CEEDAYS date validation.
     * Uses java.time.LocalDate.parse with STRICT resolver to catch
     * invalid dates like 2024-02-30 or 2023-02-29.
     */
    private boolean isValidDate(String date) {
        try {
            LocalDate.parse(date, STRICT_DATE_FORMAT);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * Replaces COBOL NUMVAL-C + MOVE to WS-TRAN-AMT-E (PIC +99999999.99).
     * Parses the signed amount string and reformats to COBOL display format.
     */
    private String normalizeAmount(String amt) {
        try {
            double val = Double.parseDouble(amt);
            return String.format("%+012.2f", val);
        } catch (NumberFormatException e) {
            throw new ValidationException(
                    "Amount should be in format -99999999.99", "amount");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
