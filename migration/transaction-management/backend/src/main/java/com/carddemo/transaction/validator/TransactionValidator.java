package com.carddemo.transaction.validator;

import com.carddemo.transaction.dto.TransactionAddRequest;
import com.carddemo.transaction.exception.TransactionValidationException;
import com.carddemo.transaction.service.DateValidationService;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * CT02 input edit rules ported 1:1 from COTRN02C's VALIDATE-INPUT-DATA-FIELDS
 * and the confirm gate in PROCESS-ENTER-KEY (COTRN02C.cbl:169-436).
 *
 * <p>The legacy program short-circuits: each failing check moves an ERRMSG and
 * PERFORMs SEND-TRNADD-SCREEN, whose trailing {@code EXEC CICS RETURN} ends the
 * transaction — so the <em>first</em> failure in source order is the one the
 * user sees. This validator reproduces that "first error wins" ordering by
 * throwing {@link TransactionValidationException} carrying the verbatim message
 * as soon as a check fails.
 *
 * <p>Order (COTRN02C.cbl):
 * <ol>
 *   <li>required-empty, in field order (cbl:251-320);</li>
 *   <li>Type CD / Category CD numeric (cbl:322-337);</li>
 *   <li>Amount format {@code -99999999.99} (cbl:339-351);</li>
 *   <li>Orig Date format {@code YYYY-MM-DD} (cbl:353-366);</li>
 *   <li>Proc Date format {@code YYYY-MM-DD} (cbl:368-381);</li>
 *   <li>Orig Date real-date via CSUTLDTC (cbl:389-407);</li>
 *   <li>Proc Date real-date via CSUTLDTC (cbl:409-427);</li>
 *   <li>Merchant ID numeric (cbl:430-436).</li>
 * </ol>
 */
@Component
public class TransactionValidator {

    // --- required-empty messages (COTRN02C.cbl:251-320) ---
    public static final String TYPE_EMPTY = "Type CD can NOT be empty...";
    public static final String CATEGORY_EMPTY = "Category CD can NOT be empty...";
    public static final String SOURCE_EMPTY = "Source can NOT be empty...";
    public static final String DESC_EMPTY = "Description can NOT be empty...";
    public static final String AMOUNT_EMPTY = "Amount can NOT be empty...";
    public static final String ORIG_DATE_EMPTY = "Orig Date can NOT be empty...";
    public static final String PROC_DATE_EMPTY = "Proc Date can NOT be empty...";
    public static final String MERCHANT_ID_EMPTY = "Merchant ID can NOT be empty...";
    public static final String MERCHANT_NAME_EMPTY = "Merchant Name can NOT be empty...";
    public static final String MERCHANT_CITY_EMPTY = "Merchant City can NOT be empty...";
    public static final String MERCHANT_ZIP_EMPTY = "Merchant Zip can NOT be empty...";

    // --- numeric messages (COTRN02C.cbl:322-337, 430-436) ---
    public static final String TYPE_NUMERIC = "Type CD must be Numeric...";
    public static final String CATEGORY_NUMERIC = "Category CD must be Numeric...";
    public static final String MERCHANT_ID_NUMERIC = "Merchant ID must be Numeric...";

    // --- format / date messages (COTRN02C.cbl:339-427) ---
    public static final String AMOUNT_FORMAT = "Amount should be in format -99999999.99";
    public static final String ORIG_DATE_FORMAT = "Orig Date should be in format YYYY-MM-DD";
    public static final String PROC_DATE_FORMAT = "Proc Date should be in format YYYY-MM-DD";
    public static final String ORIG_DATE_INVALID = "Orig Date - Not a valid date...";
    public static final String PROC_DATE_INVALID = "Proc Date - Not a valid date...";

    // --- confirm messages (COTRN02C.cbl:169-188) ---
    public static final String CONFIRM_REQUIRED = "Confirm to add this transaction...";
    public static final String CONFIRM_INVALID = "Invalid value. Valid values are (Y/N)...";

    /** Amount edit picture {@code -99999999.99}: sign, 8 digits, '.', 2 digits (cbl:340-343). */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("^[+-][0-9]{8}\\.[0-9]{2}$");
    /** Date edit picture {@code YYYY-MM-DD}: 4 digits, '-', 2 digits, '-', 2 digits (cbl:354-358). */
    private static final Pattern DATE_PATTERN = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}$");

    private final DateValidationService dateValidationService;

    public TransactionValidator(DateValidationService dateValidationService) {
        this.dateValidationService = dateValidationService;
    }

    /**
     * Run the CT02 data-field edits in COTRN02C's source order, throwing on the
     * first failure. Does not cover the account/card key resolution (that is
     * VALIDATE-INPUT-KEY-FIELDS, handled by the resolve service) or the confirm
     * gate ({@link #validateConfirm(String)}).
     */
    public void validateDataFields(TransactionAddRequest req) {
        // 1) required-empty, field order (COTRN02C.cbl:251-320).
        requireNonEmpty(req.getTypeCd(), TYPE_EMPTY);
        requireNonEmpty(req.getCategoryCd(), CATEGORY_EMPTY);
        requireNonEmpty(req.getSource(), SOURCE_EMPTY);
        requireNonEmpty(req.getDescription(), DESC_EMPTY);
        requireNonEmpty(req.getAmount(), AMOUNT_EMPTY);
        requireNonEmpty(req.getOrigDate(), ORIG_DATE_EMPTY);
        requireNonEmpty(req.getProcDate(), PROC_DATE_EMPTY);
        requireNonEmpty(req.getMerchantId(), MERCHANT_ID_EMPTY);
        requireNonEmpty(req.getMerchantName(), MERCHANT_NAME_EMPTY);
        requireNonEmpty(req.getMerchantCity(), MERCHANT_CITY_EMPTY);
        requireNonEmpty(req.getMerchantZip(), MERCHANT_ZIP_EMPTY);

        // 2) Type CD / Category CD numeric (COTRN02C.cbl:322-337).
        requireNumeric(req.getTypeCd(), TYPE_NUMERIC);
        requireNumeric(req.getCategoryCd(), CATEGORY_NUMERIC);

        // 3) Amount edit format (COTRN02C.cbl:339-351).
        if (!AMOUNT_PATTERN.matcher(req.getAmount()).matches()) {
            throw new TransactionValidationException(AMOUNT_FORMAT);
        }

        // 4) Orig/Proc date shape (COTRN02C.cbl:353-381).
        if (!DATE_PATTERN.matcher(req.getOrigDate()).matches()) {
            throw new TransactionValidationException(ORIG_DATE_FORMAT);
        }
        if (!DATE_PATTERN.matcher(req.getProcDate()).matches()) {
            throw new TransactionValidationException(PROC_DATE_FORMAT);
        }

        // 5) Orig/Proc real-date via CSUTLDTC severity gate (COTRN02C.cbl:389-427).
        if (!dateValidationService.isValid(req.getOrigDate())) {
            throw new TransactionValidationException(ORIG_DATE_INVALID);
        }
        if (!dateValidationService.isValid(req.getProcDate())) {
            throw new TransactionValidationException(PROC_DATE_INVALID);
        }

        // 6) Merchant ID numeric (COTRN02C.cbl:430-436).
        requireNumeric(req.getMerchantId(), MERCHANT_ID_NUMERIC);
    }

    /**
     * Confirm gate from PROCESS-ENTER-KEY (COTRN02C.cbl:169-188): only Y/y
     * proceeds to the WRITE. Blank / N / n -> "Confirm to add this
     * transaction..."; any other value -> "Invalid value. Valid values are
     * (Y/N)...".
     *
     * @return {@code true} when the user confirmed (Y/y) and the write should proceed
     */
    public boolean validateConfirm(String confirm) {
        String value = confirm == null ? "" : confirm.trim();
        if ("Y".equalsIgnoreCase(value)) {
            return true;
        }
        if (value.isEmpty() || "N".equalsIgnoreCase(value)) {
            throw new TransactionValidationException(CONFIRM_REQUIRED);
        }
        throw new TransactionValidationException(CONFIRM_INVALID);
    }

    private void requireNonEmpty(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new TransactionValidationException(message);
        }
    }

    private void requireNumeric(String value, String message) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty() || !v.chars().allMatch(Character::isDigit)) {
            throw new TransactionValidationException(message);
        }
    }
}
