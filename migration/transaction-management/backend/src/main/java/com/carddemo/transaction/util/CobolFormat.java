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

    /**
     * Renders the CT00 list row date exactly like COTRN00C's POPULATE-TRAN-DATA
     * (COTRN00C.cbl:383-388): it slices {@code TRAN-ORIG-TS}
     * ({@code YYYY-MM-DD-HH.MM.SS.uuuuuu}) into {@code WS-CURDATE-MM-DD-YY},
     * i.e. {@code MM/DD/YY} using the last two digits of the year.
     * Example: {@code "2023-06-01-10.15.31.000000" -> "06/01/23"}.
     * A null/short timestamp yields an empty string (the blank list cell).
     */
    public static String listDate(String origTs) {
        if (origTs == null || origTs.length() < 10) {
            return "";
        }
        String mm = origTs.substring(5, 7);
        String dd = origTs.substring(8, 10);
        String yy = origTs.substring(2, 4);
        return mm + "/" + dd + "/" + yy;
    }
}
