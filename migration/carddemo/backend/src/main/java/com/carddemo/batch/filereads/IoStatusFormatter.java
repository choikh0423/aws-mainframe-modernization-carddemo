package com.carddemo.batch.filereads;

/**
 * The {@code 9910-DISPLAY-IO-STATUS} / {@code Z-DISPLAY-IO-STATUS} paragraph shared by
 * all four S-15 programs (`CBACT01C.cbl:413-426`), reproduced verbatim (FR-G8).
 *
 * <p>The literal is {@code 'FILE STATUS IS: NNNN'} and the four rendered characters are
 * appended to it with no separator, so a status of {@code 35} prints as
 * {@code FILE STATUS IS: NNNN0035}.
 */
public final class IoStatusFormatter {

    private static final String LITERAL = "FILE STATUS IS: NNNN";

    private IoStatusFormatter() {
    }

    /** The full display line for a two-byte file status. */
    public static String line(String fileStatus) {
        return LITERAL + format(fileStatus);
    }

    /**
     * The four-character {@code IO-STATUS-04} rendering: {@code '00'} plus the status
     * for a numeric status that does not start with {@code 9}; otherwise the first byte
     * followed by the binary value of the second byte in three digits.
     */
    public static String format(String fileStatus) {
        String status = CobolPicture.text(fileStatus, 2);
        char first = status.charAt(0);
        char second = status.charAt(1);
        if (first == '9' || !isNumeric(status)) {
            return first + String.format("%03d", (int) second);
        }
        return "00" + status;
    }

    private static boolean isNumeric(String status) {
        return Character.isDigit(status.charAt(0)) && Character.isDigit(status.charAt(1));
    }
}
