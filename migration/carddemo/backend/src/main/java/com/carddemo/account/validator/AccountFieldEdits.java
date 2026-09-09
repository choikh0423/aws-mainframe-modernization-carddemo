package com.carddemo.account.validator;

import java.math.BigDecimal;

/**
 * The generic field edits COACTUPC performs (1215-EDIT-MANDATORY through 1250-EDIT-SIGNED-9V2,
 * COACTUPC:1824-2219). Each method returns the exact legacy message, or {@code null} when the
 * field passed.
 *
 * <p>Every edit works on the map field of a fixed length: COBOL tests the whole
 * {@code WS-EDIT-ALPHANUM-ONLY(1:length)}, so a four digit zip in a five character field is
 * "1234 " and fails the {@code IS NUMERIC} test rather than the length test.
 */
public final class AccountFieldEdits {

    private AccountFieldEdits() {
    }

    /** Trims to, or space pads to, the map field length COACTUPC edits. */
    public static String field(String value, int length) {
        String raw = value == null ? "" : value;
        if (raw.length() >= length) {
            return raw.substring(0, length);
        }
        StringBuilder padded = new StringBuilder(raw);
        while (padded.length() < length) {
            padded.append(' ');
        }
        return padded.toString();
    }

    /** A field the receive-map logic turned into LOW-VALUES: null, spaces or '*' (COACTUPC:1039-1428). */
    public static boolean notSupplied(String value) {
        String raw = value == null ? "" : value.trim();
        return raw.isEmpty() || "*".equals(raw);
    }

    /** 1215-EDIT-MANDATORY (COACTUPC:1824-1851). */
    public static String mandatory(String name, String value, int length) {
        return notSupplied(field(value, length)) ? name + " must be supplied." : null;
    }

    /** 1220-EDIT-YESNO (COACTUPC:1856-1892); '0' counts as not supplied. */
    public static String yesNo(String name, String value) {
        String edited = field(value, 1);
        if (notSupplied(edited) || "0".equals(edited)) {
            return name + " must be supplied.";
        }
        return "Y".equals(edited) || "N".equals(edited) ? null : name + " must be Y or N.";
    }

    /** 1225-EDIT-ALPHA-REQD (COACTUPC:1898-1950): letters and spaces only. */
    public static String alphaRequired(String name, String value, int length) {
        String edited = field(value, length);
        if (notSupplied(edited)) {
            return name + " must be supplied.";
        }
        return isAlphaOrSpace(edited) ? null : name + " can have alphabets only.";
    }

    /** 1235-EDIT-ALPHA-OPT (COACTUPC:2012-2060): blank passes. */
    public static String alphaOptional(String name, String value, int length) {
        String edited = field(value, length);
        if (notSupplied(edited)) {
            return null;
        }
        return isAlphaOrSpace(edited) ? null : name + " can have alphabets only.";
    }

    /** 1245-EDIT-NUM-REQD (COACTUPC:2109-2174). */
    public static String numericRequired(String name, String value, int length) {
        String edited = field(value, length);
        if (notSupplied(edited)) {
            return name + " must be supplied.";
        }
        if (!isAllDigits(edited)) {
            return name + " must be all numeric.";
        }
        return Long.parseLong(edited) == 0L ? name + " must not be zero." : null;
    }

    /** 1250-EDIT-SIGNED-9V2 (COACTUPC:2180-2219), i.e. FUNCTION TEST-NUMVAL-C. */
    public static String signedAmount(String name, String value) {
        if (notSupplied(value)) {
            return name + " must be supplied.";
        }
        return parseAmount(value) == null ? name + " is not valid" : null;
    }

    /**
     * FUNCTION NUMVAL-C of an argument TEST-NUMVAL-C accepted: optional currency sign, an
     * optional leading or trailing sign (or surrounding brackets, or CR/DB), comma grouping and
     * at most two decimals. Returns {@code null} when TEST-NUMVAL-C would reject the text.
     */
    public static BigDecimal parseAmount(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        boolean negative = false;
        if (text.startsWith("(") && text.endsWith(")")) {
            negative = true;
            text = text.substring(1, text.length() - 1).trim();
        }
        String upper = text.toUpperCase();
        if (upper.endsWith("CR") || upper.endsWith("DB")) {
            negative = negative || upper.endsWith("CR");
            text = text.substring(0, text.length() - 2).trim();
        }
        if (text.startsWith("+") || text.startsWith("-")) {
            negative = negative || text.charAt(0) == '-';
            text = text.substring(1).trim();
        } else if (text.endsWith("+") || text.endsWith("-")) {
            negative = negative || text.charAt(text.length() - 1) == '-';
            text = text.substring(0, text.length() - 1).trim();
        }
        if (text.startsWith("$")) {
            text = text.substring(1).trim();
        }
        if (!text.matches("[0-9]{1,3}(,[0-9]{3})*(\\.[0-9]{1,2})?|[0-9]+(\\.[0-9]{1,2})?")) {
            return null;
        }
        BigDecimal parsed = new BigDecimal(text.replace(",", ""));
        return negative ? parsed.negate() : parsed;
    }

    private static boolean isAlphaOrSpace(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isLetter(c) && c != ' ') {
                return false;
            }
        }
        return true;
    }

    private static boolean isAllDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return !value.isEmpty();
    }
}
