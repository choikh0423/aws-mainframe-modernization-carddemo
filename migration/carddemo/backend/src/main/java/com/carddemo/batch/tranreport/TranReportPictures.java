package com.carddemo.batch.tranreport;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The two edited PICTURE clauses the transaction report writes amounts with,
 * {@code PIC -ZZZ,ZZZ,ZZZ.ZZ} for the detail amount and
 * {@code PIC +ZZZ,ZZZ,ZZZ.ZZ} for the page/account/grand totals
 * (app/cpy/CVTRA07Y.cpy:30, 54, 60, 66).
 *
 * <p>Both are 15 characters wide: a fixed sign position, nine zero-suppressed
 * integer digits with comma insertion, the decimal point and two decimals.
 * Because every digit position is a suppression symbol, a zero value blanks the
 * whole field - sign, commas and decimal point included - and a value below 1
 * suppresses up to the decimal point only.
 *
 * <p>{@code com.carddemo.common.util.CobolFormat} carries the estate's shared
 * pictures but not these two, and shared code is read-only to a stream, so they
 * live here.
 */
final class TranReportPictures {

    /** Width of both edited fields. */
    static final int WIDTH = 15;

    /** Digit positions in {@code S9(09)V99}: nine integer plus two decimal. */
    private static final int DIGITS = 11;
    private static final int SCALE = 2;

    private TranReportPictures() {
    }

    /**
     * {@code PIC -ZZZ,ZZZ,ZZZ.ZZ}: the sign position holds '-' for a negative
     * value and a space for anything else.
     */
    static String detailAmount(BigDecimal amount) {
        return edited(amount, false);
    }

    /**
     * {@code PIC +ZZZ,ZZZ,ZZZ.ZZ}: the sign position always holds '+' or '-'.
     */
    static String totalAmount(BigDecimal amount) {
        return edited(amount, true);
    }

    private static String edited(BigDecimal amount, boolean signAlways) {
        // A COBOL MOVE truncates the excess low-order and high-order digits
        // rather than rounding or overflowing.
        BigDecimal scaled = amount.setScale(SCALE, RoundingMode.DOWN);
        String digits = scaled.abs().unscaledValue().toString();
        digits = digits.length() > DIGITS
                ? digits.substring(digits.length() - DIGITS)
                : "0".repeat(DIGITS - digits.length()) + digits;

        if (digits.chars().allMatch(c -> c == '0')) {
            return " ".repeat(WIDTH);
        }

        String grouped = digits.substring(0, 3) + "," + digits.substring(3, 6)
                + "," + digits.substring(6, 9);
        StringBuilder integerPart = new StringBuilder(grouped.length());
        boolean suppressing = true;
        for (char c : grouped.toCharArray()) {
            if (suppressing && (c == '0' || c == ',')) {
                integerPart.append(' ');
                continue;
            }
            suppressing = false;
            integerPart.append(c);
        }

        char sign = scaled.signum() < 0 ? '-' : (signAlways ? '+' : ' ');
        return sign + integerPart.toString() + "." + digits.substring(9);
    }
}
