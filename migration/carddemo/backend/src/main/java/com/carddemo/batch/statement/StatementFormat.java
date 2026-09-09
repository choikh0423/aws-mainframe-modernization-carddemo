package com.carddemo.batch.statement;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * COBOL PICTURE and STRING semantics used by CBSTM03A and by the COSTM01 work
 * record, kept in one place so the statement output is byte-for-byte the same as
 * the legacy report.
 *
 * <p>{@code com.carddemo.common.util.CobolFormat} renders the online screens'
 * {@code +99999999.99} field; the statement uses different pictures
 * ({@code 9(9).99-} and {@code Z(9).99-}) and needs zoned-decimal output for the
 * work file, so those live here rather than in the shared helper.
 */
final class StatementFormat {

    private static final String POSITIVE_OVERPUNCH = "{ABCDEFGHI";
    private static final String NEGATIVE_OVERPUNCH = "}JKLMNOPQR";

    /** The 9 integer digits of PIC 9(9)V99 / S9(9)V99: anything above is truncated. */
    private static final BigDecimal EDITED_MODULUS = new BigDecimal("1000000000");

    private StatementFormat() {
    }

    /** MOVE to PIC X(n): left justified, blank padded, truncated on the right. */
    static String text(String value, int length) {
        String source = value == null ? "" : value;
        if (source.length() >= length) {
            return source.substring(0, length);
        }
        return source + " ".repeat(length - source.length());
    }

    /** MOVE to PIC 9(n): zero padded, high-order digits truncated. */
    static String digits(long value, int length) {
        String plain = Long.toString(Math.abs(value));
        if (plain.length() >= length) {
            return plain.substring(plain.length() - length);
        }
        return "0".repeat(length - plain.length()) + plain;
    }

    /**
     * PIC S9(a)V9(b) DISPLAY: zoned decimal whose sign is an overpunch on the last
     * digit, the encoding {@code FixedWidthRecord#signed} reads back.
     */
    static String zoned(BigDecimal value, int length, int scale) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        BigDecimal unscaled = amount.abs().setScale(scale, RoundingMode.DOWN).movePointRight(scale);
        String plain = digits(unscaled.longValueExact(), length);
        char lastDigit = plain.charAt(plain.length() - 1);
        String overpunch = amount.signum() < 0 ? NEGATIVE_OVERPUNCH : POSITIVE_OVERPUNCH;
        return plain.substring(0, plain.length() - 1) + overpunch.charAt(lastDigit - '0');
    }

    /**
     * PIC 9(9).99- (CBSTM03A.CBL:113, ST-CURR-BAL): nine zero-padded integer
     * digits, a point, two decimals and a trailing sign position that holds
     * {@code -} for a negative value and a blank otherwise.
     */
    static String amountPic9(BigDecimal value) {
        return edited(value, false);
    }

    /**
     * PIC Z(9).99- (CBSTM03A.CBL:137, 142, ST-TRANAMT / ST-TOTAL-TRAMT): as
     * {@link #amountPic9} but with leading zeros replaced by blanks. A zero value
     * blanks all nine integer positions and still prints {@code .00}.
     */
    static String amountPicZ(BigDecimal value) {
        return edited(value, true);
    }

    private static String edited(BigDecimal value, boolean suppressLeadingZeros) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        BigDecimal magnitude = amount.abs().setScale(2, RoundingMode.DOWN).remainder(EDITED_MODULUS);
        String plain = magnitude.toPlainString();
        int point = plain.indexOf('.');
        String integerPart = digits(Long.parseLong(plain.substring(0, point)), 9);
        String decimals = plain.substring(point + 1);
        if (suppressLeadingZeros) {
            integerPart = suppress(integerPart);
        }
        return integerPart + "." + decimals + (amount.signum() < 0 ? "-" : " ");
    }

    private static String suppress(String integerPart) {
        StringBuilder suppressed = new StringBuilder(integerPart);
        for (int i = 0; i < suppressed.length() && suppressed.charAt(i) == '0'; i++) {
            suppressed.setCharAt(i, ' ');
        }
        return suppressed.toString();
    }

    /** STRING ... DELIMITED BY ' ': the value up to its first blank. */
    static String upToBlank(String value) {
        return upTo(value, " ");
    }

    /** STRING ... DELIMITED BY '  ': the value up to its first double blank. */
    static String upToDoubleBlank(String value) {
        return upTo(value, "  ");
    }

    private static String upTo(String value, String delimiter) {
        if (value == null) {
            return "";
        }
        int end = value.indexOf(delimiter);
        return end < 0 ? value : value.substring(0, end);
    }
}
