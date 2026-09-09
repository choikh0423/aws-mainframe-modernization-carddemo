package com.carddemo.batch.filereads;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Renders values back into the COBOL PICTURE representations the S-15 print jobs
 * emitted, so a {@code DISPLAY} of a record or a written output record is
 * byte-identical to the mainframe's.
 *
 * <p>It is the inverse of {@link com.carddemo.common.batch.FixedWidthRecord}, and
 * uses the same zoned-decimal overpunch convention as the ASCII unloads in
 * {@code app/data/ASCII/**} (FR-G9).
 */
public final class CobolPicture {

    private static final String POSITIVE_OVERPUNCH = "{ABCDEFGHI";
    private static final String NEGATIVE_OVERPUNCH = "}JKLMNOPQR";

    private CobolPicture() {
    }

    /** {@code PIC X(n)}: left justified, space padded, truncated at {@code length}. */
    public static String text(String value, int length) {
        String raw = value == null ? "" : value;
        if (raw.length() >= length) {
            return raw.substring(0, length);
        }
        return raw + " ".repeat(length - raw.length());
    }

    /**
     * {@code PIC 9(n)}: an unsigned zero-padded zoned integer. A value too large for
     * the picture loses its high-order digits, as a COBOL {@code MOVE} does.
     */
    public static String unsigned(Number value, int digits) {
        long raw = value == null ? 0L : value.longValue();
        String formatted = String.format("%0" + digits + "d", Math.abs(raw));
        return formatted.substring(formatted.length() - digits);
    }

    /**
     * {@code PIC S9(i)V9(s)} DISPLAY: {@code i + s} zoned digits with the sign carried
     * as an overpunch on the final digit ({@code 194.00 -> "00000001940{"}).
     */
    public static String signed(BigDecimal value, int intDigits, int scale) {
        BigDecimal raw = value == null ? BigDecimal.ZERO : value;
        BigDecimal scaled = raw.setScale(scale, RoundingMode.DOWN);
        String digits = String.format("%0" + (intDigits + scale) + "d", scaled.abs().unscaledValue());
        digits = digits.substring(digits.length() - (intDigits + scale));
        int last = digits.charAt(digits.length() - 1) - '0';
        String overpunch = scaled.signum() < 0 ? NEGATIVE_OVERPUNCH : POSITIVE_OVERPUNCH;
        return digits.substring(0, digits.length() - 1) + overpunch.charAt(last);
    }

    /**
     * {@code PIC S9(i)V9(s) COMP-3}: packed decimal, two digits per byte, sign in the
     * low nibble of the last byte ({@code 0x0C} positive, {@code 0x0D} negative). The
     * field occupies {@code (i + s) / 2 + 1} bytes, leading nibbles zero filled.
     */
    public static byte[] packed(BigDecimal value, int intDigits, int scale) {
        BigDecimal raw = value == null ? BigDecimal.ZERO : value;
        BigDecimal scaled = raw.setScale(scale, RoundingMode.DOWN);
        int totalDigits = intDigits + scale;
        String digits = String.format("%0" + totalDigits + "d", scaled.abs().unscaledValue());
        digits = digits.substring(digits.length() - totalDigits);

        int length = totalDigits / 2 + 1;
        byte[] packed = new byte[length];
        // Nibbles are filled from the right: sign first, then the digits backwards.
        int nibble = 2 * length - 1;
        packed[nibble / 2] = (byte) (scaled.signum() < 0 ? 0x0D : 0x0C);
        nibble--;
        for (int i = digits.length() - 1; i >= 0 && nibble >= 0; i--, nibble--) {
            int digit = digits.charAt(i) - '0';
            int index = nibble / 2;
            if (nibble % 2 == 0) {
                packed[index] |= (byte) (digit << 4);
            } else {
                packed[index] |= (byte) digit;
            }
        }
        return packed;
    }
}
