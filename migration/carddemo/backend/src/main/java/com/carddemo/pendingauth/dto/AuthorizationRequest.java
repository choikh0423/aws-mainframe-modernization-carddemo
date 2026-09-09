package com.carddemo.pendingauth.dto;

import java.math.BigDecimal;

/**
 * {@code CCPAURQY.cpy} as it arrives on {@code AWS.M2.CARDDEMO.PAUTH.REQUEST}:
 * eighteen comma-delimited fields in copybook order, unstrung by COPAUA0C's
 * 2100-EXTRACT-REQUEST-MSG (COPAUA0C.cbl:354-379).
 *
 * <p>The ninth field is read into {@code WS-TRANSACTION-AMT-AN PIC X(13)} and
 * converted with {@code FUNCTION NUMVAL}, so a signed, spaced or unpadded amount
 * is accepted exactly as NUMVAL would take it.
 */
public record AuthorizationRequest(String authDate,
                                   String authTime,
                                   String cardNum,
                                   String authType,
                                   String cardExpiryDate,
                                   String messageType,
                                   String messageSource,
                                   String processingCode,
                                   BigDecimal transactionAmt,
                                   String merchantCategoryCode,
                                   String acqrCountryCode,
                                   String posEntryMode,
                                   String merchantId,
                                   String merchantName,
                                   String merchantCity,
                                   String merchantState,
                                   String merchantZip,
                                   String transactionId) {

    /** The number of comma-delimited fields the UNSTRING consumes. */
    public static final int FIELD_COUNT = 18;

    /**
     * {@code UNSTRING ... DELIMITED BY ','}: missing trailing fields stay blank
     * (the receiving items keep their spaces), surplus fields are ignored.
     */
    public static AuthorizationRequest parse(String message) {
        String[] parts = (message == null ? "" : message).split(",", -1);
        String[] f = new String[FIELD_COUNT];
        for (int i = 0; i < FIELD_COUNT; i++) {
            f[i] = i < parts.length ? parts[i] : "";
        }
        return new AuthorizationRequest(
                trim(f[0]), trim(f[1]), trim(f[2]), trim(f[3]), trim(f[4]), trim(f[5]),
                trim(f[6]), trim(f[7]), numval(f[8]), trim(f[9]), trim(f[10]), trim(f[11]),
                trim(f[12]), trim(f[13]), trim(f[14]), trim(f[15]), trim(f[16]), trim(f[17]));
    }

    /**
     * {@code FUNCTION NUMVAL}: leading/trailing blanks and a leading or trailing
     * sign are accepted; anything else that is not a digit or decimal point is
     * ignored, and an empty field is zero.
     */
    static BigDecimal numval(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            return BigDecimal.ZERO;
        }
        boolean negative = s.startsWith("-") || s.endsWith("-");
        StringBuilder digits = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c) || (c == '.' && digits.indexOf(".") < 0)) {
                digits.append(c);
            }
        }
        if (digits.length() == 0 || ".".contentEquals(digits)) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = new BigDecimal(digits.toString());
        return negative ? value.negate() : value;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
