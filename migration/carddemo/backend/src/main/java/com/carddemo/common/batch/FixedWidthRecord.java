package com.carddemo.common.batch;

import java.math.BigDecimal;

/**
 * Reads COBOL DISPLAY fields out of a fixed-width record line, the way a batch
 * program's FD record layout does.
 *
 * <p>Offsets are zero-based and expressed in the copybook's own order, so a
 * layout can be transcribed field by field from the copybook. Signed
 * {@code PIC S9(a)V9(b)} DISPLAY fields carry their sign as an overpunch on the
 * final digit, which {@link #signed} decodes.
 */
public final class FixedWidthRecord {

    private static final String POSITIVE_OVERPUNCH = "{ABCDEFGHI";
    private static final String NEGATIVE_OVERPUNCH = "}JKLMNOPQR";

    private final String line;

    public FixedWidthRecord(String line, int recordLength) {
        this.line = line.length() >= recordLength ? line : padRight(line, recordLength);
    }

    /** PIC X(n): the field with COBOL's trailing space padding removed. */
    public String text(int offset, int length) {
        return line.substring(offset, offset + length).stripTrailing();
    }

    /** PIC 9(n): an unsigned zoned-decimal integer; an all-blank field reads as 0. */
    public long number(int offset, int length) {
        String raw = line.substring(offset, offset + length).trim();
        return raw.isEmpty() ? 0L : Long.parseLong(raw);
    }

    /** PIC S9(a)V9(b): a signed zoned decimal with {@code scale} implied decimals. */
    public BigDecimal signed(int offset, int length, int scale) {
        String raw = line.substring(offset, offset + length);
        char last = raw.charAt(raw.length() - 1);
        String digits = raw.substring(0, raw.length() - 1);

        int positive = POSITIVE_OVERPUNCH.indexOf(last);
        int negative = NEGATIVE_OVERPUNCH.indexOf(last);
        String sign;
        char lastDigit;
        if (positive >= 0) {
            sign = "";
            lastDigit = (char) ('0' + positive);
        } else if (negative >= 0) {
            sign = "-";
            lastDigit = (char) ('0' + negative);
        } else {
            sign = "";
            lastDigit = last;
        }
        return new BigDecimal(sign + digits + lastDigit).movePointLeft(scale);
    }

    private static String padRight(String value, int length) {
        return value + " ".repeat(length - value.length());
    }
}
