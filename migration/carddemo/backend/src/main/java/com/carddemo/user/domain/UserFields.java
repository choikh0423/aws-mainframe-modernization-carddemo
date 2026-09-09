package com.carddemo.user.domain;

/**
 * USRSEC field widths and the COBOL {@code MOVE} semantics S-05 must reproduce
 * (FR-US-2): a screen field is a fixed-width area, so a longer value is
 * truncated to the picture and trailing blanks are not significant
 * (app/cpy/CSUSR01Y.cpy:17-23).
 */
public final class UserFields {

    public static final int USER_ID_LEN = 8;
    public static final int FIRST_NAME_LEN = 20;
    public static final int LAST_NAME_LEN = 20;
    public static final int PASSWORD_LEN = 8;
    public static final int USER_TYPE_LEN = 1;

    private UserFields() {
    }

    /** {@code MOVE} a screen value into a {@code PIC X(len)} field. */
    public static String fit(String value, int len) {
        String v = value == null ? "" : value.trim();
        return v.length() <= len ? v : v.substring(0, len);
    }

    /** The COBOL {@code = SPACES OR LOW-VALUES} test. */
    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
