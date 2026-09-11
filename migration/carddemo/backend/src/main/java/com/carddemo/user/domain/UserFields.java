package com.carddemo.user.domain;

/**
 * USRSEC field widths and the COBOL {@code MOVE} semantics S-05 must reproduce
 * (FR-US-2): a screen field is a fixed-width area, so a longer value is
 * truncated to the picture by position and only trailing blanks are
 * insignificant (app/cpy/CSUSR01Y.cpy:17-23).
 */
public final class UserFields {

    public static final int USER_ID_LEN = 8;
    public static final int FIRST_NAME_LEN = 20;
    public static final int LAST_NAME_LEN = 20;
    public static final int PASSWORD_LEN = 8;
    public static final int USER_TYPE_LEN = 1;

    private UserFields() {
    }

    /**
     * {@code MOVE} a screen value into a {@code PIC X(len)} field: the value is
     * cut at the picture width and then padded with blanks, so leading blanks
     * occupy positions and survive, while the padding at the right does not.
     */
    public static String fit(String value, int len) {
        String v = value == null ? "" : value;
        if (v.length() > len) {
            v = v.substring(0, len);
        }
        int end = v.length();
        while (end > 0 && v.charAt(end - 1) == ' ') {
            end--;
        }
        return v.substring(0, end);
    }

    /** The COBOL {@code = SPACES OR LOW-VALUES} test. */
    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
