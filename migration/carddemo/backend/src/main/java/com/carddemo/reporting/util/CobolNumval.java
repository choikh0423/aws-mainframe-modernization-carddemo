package com.carddemo.reporting.util;

import java.math.BigDecimal;

/**
 * {@code FUNCTION NUMVAL-C} followed by a {@code MOVE} into an unsigned
 * {@code PIC 9(n)} field, as CORPT00C applies it to every typed date component
 * (cbl:305-327).
 *
 * <p>NUMVAL-C reads the numeric value out of a free-form string: leading and
 * trailing blanks, a currency sign, digit-group separators and a sign (leading
 * {@code + -} or trailing {@code + - CR DB}) are all ignored. Characters that do
 * not form a number yield zero, which is why CORPT00C never reaches its
 * "Not a valid Year..." branches (FR-R25).
 *
 * <p>The receiving field is unsigned and has a fixed number of digits, so the
 * value is truncated towards zero, made absolute, and its high-order digits are
 * dropped exactly as a {@code COMPUTE} without {@code ON SIZE ERROR} does:
 * {@code 123} into {@code PIC 99} stores {@code 23}.
 */
public final class CobolNumval {

    private CobolNumval() {
    }

    /**
     * Apply {@code COMPUTE <PIC 9(digits)> = FUNCTION NUMVAL-C(text)} and return
     * the receiving field's display value, zero-padded to {@code digits}.
     */
    public static String numvalCInto(String text, int digits) {
        long value = Math.abs(numvalC(text).toBigInteger().longValue());
        long modulus = (long) Math.pow(10, digits);
        return String.format("%0" + digits + "d", value % modulus);
    }

    /** The raw {@code FUNCTION NUMVAL-C} result; zero when the argument holds no number. */
    public static BigDecimal numvalC(String text) {
        if (text == null) {
            return BigDecimal.ZERO;
        }
        String s = text.trim();
        if (s.isEmpty()) {
            return BigDecimal.ZERO;
        }
        boolean negative = false;
        if (s.endsWith("CR") || s.endsWith("cr") || s.endsWith("DB") || s.endsWith("db")) {
            negative = true;
            s = s.substring(0, s.length() - 2).trim();
        } else if (s.endsWith("-")) {
            negative = true;
            s = s.substring(0, s.length() - 1).trim();
        } else if (s.endsWith("+")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        if (s.startsWith("-")) {
            negative = !negative;
            s = s.substring(1).trim();
        } else if (s.startsWith("+")) {
            s = s.substring(1).trim();
        }
        // NUMVAL-C ignores the currency sign and the digit separators.
        StringBuilder digits = new StringBuilder();
        boolean seenPoint = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            } else if (c == '.' && !seenPoint) {
                seenPoint = true;
                digits.append('.');
            }
        }
        String numeric = digits.toString();
        if (numeric.isEmpty() || numeric.equals(".")) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = new BigDecimal(numeric.startsWith(".") ? "0" + numeric : numeric);
        return negative ? value.negate() : value;
    }
}
