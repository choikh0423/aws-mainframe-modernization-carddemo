package com.carddemo.transaction.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * COBOL PICTURE-accurate output helpers for the Transaction Management stream.
 * Kept tiny and dependency-free so every migrated screen renders amounts the
 * same way the legacy 3270 maps did.
 */
public final class CobolFormat {

    private CobolFormat() {
    }

    /**
     * Renders a signed amount exactly like COTRN01C's display field
     * {@code WS-TRAN-AMT PIC +99999999.99} (COTRN01C.cbl:49, 177, 183):
     * a leading sign, 8 zero-padded integer digits, a literal '.', and 2
     * decimals. Examples: {@code 13.75 -> "+00000013.75"},
     * {@code -25.00 -> "-00000025.00"}.
     */
    public static String amountEdited(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        BigDecimal abs = amount.abs().setScale(2, RoundingMode.HALF_UP);
        String sign = amount.signum() < 0 ? "-" : "+";
        String[] parts = abs.toPlainString().split("\\.");
        String intPart = String.format("%08d", Long.parseLong(parts[0]));
        return sign + intPart + "." + parts[1];
    }
}
