package com.carddemo.mqinquiry.message;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The byte-level rules the MQ inquiry servers write their messages with: a
 * 1000-byte buffer, `PIC X(n)` space padding, `PIC 9(n)` zero padding and
 * `PIC S9(a)V9(b)` zoned decimal with the sign overpunched on the last digit
 * (COACCT01.cbl:130-169, :467-468).
 *
 * <p>The overpunch alphabet is the one the ASCII unloads in app/data/ASCII use
 * and {@code com.carddemo.common.batch.FixedWidthRecord} decodes.
 */
public final class MqInquiryLayout {

    /** MQ-BUFFER PIC X(1000) — every request and reply is exactly this long. */
    public static final int MESSAGE_LENGTH = 1000;

    private static final String POSITIVE_OVERPUNCH = "{ABCDEFGHI";
    private static final String NEGATIVE_OVERPUNCH = "}JKLMNOPQR";

    private MqInquiryLayout() {
    }

    /** PIC X(length): left-justified, space-padded, truncated when too long. */
    public static String alphanumeric(String value, int length) {
        String raw = value == null ? "" : value;
        if (raw.length() >= length) {
            return raw.substring(0, length);
        }
        return raw + " ".repeat(length - raw.length());
    }

    /** PIC 9(length): right-justified, zero-padded. */
    public static String unsigned(long value, int length) {
        return String.format("%0" + length + "d", value);
    }

    /**
     * PIC S9(integerDigits)V9(scale) DISPLAY: {@code integerDigits + scale}
     * bytes, no decimal point, the sign carried as an overpunch on the last
     * digit. 1940.00 with 10 integer digits and scale 2 is
     * <code>00000019400{</code>.
     */
    public static String signed(BigDecimal value, int integerDigits, int scale) {
        BigDecimal scaled = (value == null ? BigDecimal.ZERO : value).setScale(scale, RoundingMode.HALF_UP);
        String digits = unsigned(scaled.abs().unscaledValue().longValueExact(), integerDigits + scale);
        char lastDigit = digits.charAt(digits.length() - 1);
        String alphabet = scaled.signum() < 0 ? NEGATIVE_OVERPUNCH : POSITIVE_OVERPUNCH;
        return digits.substring(0, digits.length() - 1) + alphabet.charAt(lastDigit - '0');
    }

    /** Space-pads (or truncates) a payload to the 1000-byte MQ buffer. */
    public static String message(String payload) {
        return alphanumeric(payload, MESSAGE_LENGTH);
    }
}
