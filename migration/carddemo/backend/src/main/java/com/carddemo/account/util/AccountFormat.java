package com.carddemo.account.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * The BMS/COBOL editing the two account maps apply.
 *
 * <p>Amounts are edited with {@code +ZZZ,ZZZ,ZZZ.99} — the CACTVWA output PICOUT
 * (COACTVW.bms:120,141,162,174,195) and, on the update map, {@code WS-EDIT-CURRENCY-9-2-F}
 * (COACTUPC:371, 2797-2811). Both produce a 15 character field: a mandatory sign, the integer
 * part blank suppressed and comma grouped, then two decimals.
 */
public final class AccountFormat {

    private static final int EDITED_INTEGER_LENGTH = 11;

    private AccountFormat() {
    }

    /**
     * {@code +ZZZ,ZZZ,ZZZ.99}: a fixed sign in column 1, the comma grouped integer part blank
     * suppressed and right aligned in the next eleven columns, then {@code .} and two decimals.
     */
    public static String amount(BigDecimal value) {
        BigDecimal scaled = value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
        DecimalFormat format = new DecimalFormat("#,##0", new DecimalFormatSymbols(Locale.US));
        BigDecimal absolute = scaled.abs();
        String integerPart = format.format(absolute.toBigInteger());
        String decimals = absolute.remainder(BigDecimal.ONE)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .toBigInteger()
                .toString();
        StringBuilder edited = new StringBuilder();
        edited.append(scaled.signum() < 0 ? '-' : '+');
        for (int i = integerPart.length(); i < EDITED_INTEGER_LENGTH; i++) {
            edited.append(' ');
        }
        edited.append(integerPart).append('.');
        for (int i = decimals.length(); i < 2; i++) {
            edited.append('0');
        }
        return edited.append(decimals).toString();
    }

    /** Account ids are 11 digits, zero padded (COACTVW.bms PICIN='99999999999'). */
    public static String accountId(Long accountId) {
        return accountId == null ? "" : String.format("%011d", accountId);
    }

    /** Customer ids are 9 digits, zero padded (COACTVWC:1000-1009). */
    public static String customerId(Long customerId) {
        return customerId == null ? "" : String.format("%09d", customerId);
    }

    /** SSN is stored as 9(09) and displayed 999-99-9999 (COACTVWC:1010-1017). */
    public static String ssn(Long ssn) {
        if (ssn == null) {
            return "";
        }
        String digits = String.format("%09d", ssn);
        return digits.substring(0, 3) + "-" + digits.substring(3, 5) + "-" + digits.substring(5);
    }

    /** FICO is a 3 digit field (COACTUP.bms ACSTFCO). */
    public static String fico(Integer fico) {
        return fico == null ? "" : String.format("%03d", fico);
    }

    /** Slice of a stored {@code CCYY-MM-DD} date, tolerating short or missing values. */
    public static String datePart(String storedDate, int beginIndex, int endIndex) {
        if (storedDate == null || storedDate.length() < endIndex) {
            return "";
        }
        return storedDate.substring(beginIndex, endIndex);
    }

    /** Slice of a stored {@code (AAA)PPP-LLLL} phone number, tolerating short or missing values. */
    public static String phonePart(String storedPhone, int beginIndex, int endIndex) {
        if (storedPhone == null || storedPhone.length() < endIndex) {
            return "";
        }
        return storedPhone.substring(beginIndex, endIndex);
    }
}
