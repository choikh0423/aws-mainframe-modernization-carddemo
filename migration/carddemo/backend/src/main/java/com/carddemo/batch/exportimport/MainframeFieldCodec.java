package com.carddemo.batch.exportimport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

/**
 * Reads and writes the COBOL storage forms used by {@code CVEXPORT.cpy}.
 *
 * <p>Unlike every other CardDemo file, the S-16 export record is not printable
 * text: it mixes {@code PIC X} and {@code DISPLAY} fields with binary
 * ({@code COMP}) and packed-decimal ({@code COMP-3}) ones, so the stream needs a
 * codec that works on a byte array rather than on a line of text
 * ({@code com.carddemo.common.batch.FixedWidthRecord} covers the DISPLAY-only
 * unloads).
 *
 * <p>Conventions, all straight from Enterprise COBOL:
 * <ul>
 *   <li>text - left justified, padded with spaces to the picture length;</li>
 *   <li>zoned - {@code PIC 9(n)} DISPLAY, zero padded; signed
 *       {@code PIC S9(a)V9(b)} carries its sign as an overpunch on the last
 *       digit ({@code {}=+0 … I=+9}, {@code }=-0 … R=-9});</li>
 *   <li>binary - {@code COMP}, big endian two's complement;</li>
 *   <li>packed - {@code COMP-3}, two digits per byte with the sign in the low
 *       nibble of the last byte: {@code C} positive, {@code D} negative,
 *       {@code F} unsigned.</li>
 * </ul>
 *
 * <p>Characters are ISO-8859-1, matching the ASCII unloads in
 * {@code app/data/ASCII} that the rest of the estate runs on.
 */
public final class MainframeFieldCodec {

    static final String POSITIVE_OVERPUNCH = "{ABCDEFGHI";
    static final String NEGATIVE_OVERPUNCH = "}JKLMNOPQR";

    private MainframeFieldCodec() {
    }

    /** PIC X(length): left justified, space padded, truncated if too long. */
    public static void putText(byte[] record, int offset, int length, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.ISO_8859_1);
        int copied = Math.min(bytes.length, length);
        System.arraycopy(bytes, 0, record, offset, copied);
        for (int i = offset + copied; i < offset + length; i++) {
            record[i] = ' ';
        }
    }

    /** PIC X(length) with COBOL's trailing space padding removed. */
    public static String getText(byte[] record, int offset, int length) {
        return new String(record, offset, length, StandardCharsets.ISO_8859_1).stripTrailing();
    }

    /** PIC 9(length) DISPLAY: an unsigned zero-padded integer. */
    public static void putZoned(byte[] record, int offset, int length, long value) {
        putText(record, offset, length, digits(BigDecimal.valueOf(value), 0, length));
    }

    /** Reads a PIC 9(length) DISPLAY field; an all-blank field reads as 0. */
    public static long getZoned(byte[] record, int offset, int length) {
        String raw = new String(record, offset, length, StandardCharsets.ISO_8859_1).trim();
        return raw.isEmpty() ? 0L : Long.parseLong(raw);
    }

    /** PIC S9(a)V9(b) DISPLAY: {@code length} digits, sign as trailing overpunch. */
    public static void putZonedSigned(byte[] record, int offset, int length, BigDecimal value, int scale) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        String digits = digits(amount, scale, length);
        String alphabet = amount.signum() < 0 ? NEGATIVE_OVERPUNCH : POSITIVE_OVERPUNCH;
        char overpunch = alphabet.charAt(digits.charAt(length - 1) - '0');
        putText(record, offset, length, digits.substring(0, length - 1) + overpunch);
    }

    /** Reads a PIC S9(a)V9(b) DISPLAY field written by {@link #putZonedSigned}. */
    public static BigDecimal getZonedSigned(byte[] record, int offset, int length, int scale) {
        String raw = new String(record, offset, length, StandardCharsets.ISO_8859_1);
        char last = raw.charAt(length - 1);
        int positive = POSITIVE_OVERPUNCH.indexOf(last);
        int negative = NEGATIVE_OVERPUNCH.indexOf(last);
        String sign = negative >= 0 ? "-" : "";
        char lastDigit = positive >= 0 ? (char) ('0' + positive)
                : negative >= 0 ? (char) ('0' + negative)
                : last;
        return new BigDecimal(sign + raw.substring(0, length - 1) + lastDigit).movePointLeft(scale);
    }

    /** PIC 9(n) COMP / PIC S9(a)V9(b) COMP: big endian two's complement. */
    public static void putBinary(byte[] record, int offset, int length, long value) {
        for (int i = length - 1; i >= 0; i--) {
            record[offset + i] = (byte) (value & 0xFF);
            value >>= 8;
        }
    }

    /** Reads a COMP field written by {@link #putBinary}. */
    public static long getBinary(byte[] record, int offset, int length) {
        long value = record[offset];
        for (int i = 1; i < length; i++) {
            value = (value << 8) | (record[offset + i] & 0xFF);
        }
        return value;
    }

    /** PIC S9(a)V9(b) COMP: a scaled decimal held as a binary integer. */
    public static void putBinaryDecimal(byte[] record, int offset, int length, BigDecimal value, int scale) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        putBinary(record, offset, length, amount.setScale(scale, RoundingMode.HALF_UP).unscaledValue().longValueExact());
    }

    /** Reads a scaled COMP field written by {@link #putBinaryDecimal}. */
    public static BigDecimal getBinaryDecimal(byte[] record, int offset, int length, int scale) {
        return BigDecimal.valueOf(getBinary(record, offset, length), scale);
    }

    /**
     * PIC S9(a)V9(b) COMP-3: {@code length * 2 - 1} digits plus a sign nibble.
     *
     * @param signed {@code false} for an unsigned PIC 9 field, whose sign nibble is {@code F}
     */
    public static void putPacked(byte[] record, int offset, int length, BigDecimal value, int scale, boolean signed) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        String digits = digits(amount, scale, length * 2 - 1);
        int signNibble = !signed ? 0x0F : amount.signum() < 0 ? 0x0D : 0x0C;
        for (int i = 0; i < length; i++) {
            int high = digits.charAt(i * 2) - '0';
            int low = i == length - 1 ? signNibble : digits.charAt(i * 2 + 1) - '0';
            record[offset + i] = (byte) ((high << 4) | low);
        }
    }

    /** Reads a COMP-3 field written by {@link #putPacked}. */
    public static BigDecimal getPacked(byte[] record, int offset, int length, int scale) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int b = record[offset + i] & 0xFF;
            digits.append((char) ('0' + (b >> 4)));
            if (i < length - 1) {
                digits.append((char) ('0' + (b & 0x0F)));
            }
        }
        boolean negative = (record[offset + length - 1] & 0x0F) == 0x0D;
        return new BigDecimal((negative ? "-" : "") + digits).movePointLeft(scale);
    }

    /**
     * The digit string COBOL stores for {@code value} in a field of
     * {@code digitCount} digits with {@code scale} implied decimals: absolute
     * value, rescaled, zero padded, high-order digits dropped on overflow just
     * as a COBOL MOVE truncates them.
     */
    private static String digits(BigDecimal value, int scale, int digitCount) {
        String unscaled = value.abs().setScale(scale, RoundingMode.HALF_UP).unscaledValue().toString();
        if (unscaled.length() > digitCount) {
            return unscaled.substring(unscaled.length() - digitCount);
        }
        return "0".repeat(digitCount - unscaled.length()) + unscaled;
    }
}
