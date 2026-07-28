package com.carddemo.transaction.service;

/**
 * Helpers reproducing COBOL semantics on fixed-length alphanumeric screen
 * fields: values are space padded to the BMS field length, so an "IS NUMERIC"
 * test only succeeds when the whole field is filled with digits.
 */
public final class CobolField {

    private CobolField() {
    }

    /** Space pads (or truncates) a screen input to its BMS field length. */
    public static String pad(String value, int length) {
        String v = value == null ? "" : value;
        if (v.length() >= length) {
            return v.substring(0, length);
        }
        return v + " ".repeat(length - v.length());
    }

    /** COBOL "= SPACES OR LOW-VALUES" test. */
    public static boolean isEmpty(String value) {
        return value == null || value.isBlank();
    }

    /** COBOL "IS NUMERIC" test on the space padded field. */
    public static boolean isNumeric(String value, int length) {
        String padded = pad(value, length);
        for (int i = 0; i < padded.length(); i++) {
            if (!Character.isDigit(padded.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Character at 1-based COBOL reference-modification position. */
    public static char charAt(String padded, int position) {
        return padded.charAt(position - 1);
    }

    /** Substring using 1-based COBOL reference modification (offset:length). */
    public static String refmod(String padded, int position, int length) {
        return padded.substring(position - 1, position - 1 + length);
    }

    public static boolean allDigits(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** MOVE of a numeric value to PIC 9(n), i.e. right justified, zero filled. */
    public static String zeroFill(String digits, int length) {
        String trimmed = digits.trim();
        String stripped = trimmed.replaceFirst("^0+(?=.)", "");
        if (stripped.length() >= length) {
            return stripped.substring(stripped.length() - length);
        }
        return "0".repeat(length - stripped.length()) + stripped;
    }
}
