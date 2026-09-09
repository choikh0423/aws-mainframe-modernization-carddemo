package com.carddemo.pendingauth.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * COBOL PICTURE-accurate output helpers for the PendingAuthorizations screens
 * (COPAUS0C / COPAUS1C) and the CP00 MQ reply (COPAUA0C).
 *
 * <p>The legacy programs move packed amounts through edited working-storage
 * fields before they reach the map or the reply buffer, so the wire format is
 * the edited picture, not the raw number:
 * {@code WS-AUTH-AMT / WS-DISPLAY-AMT12 PIC -zzzzzzz9.99} (COPAUS0C.cbl:55-56),
 * {@code WS-DISPLAY-AMT9 PIC -zzzz9.99} (COPAUS0C.cbl:57),
 * {@code WS-DISPLAY-COUNT PIC 9(03)} (COPAUS0C.cbl:58) and
 * {@code WS-APPROVED-AMT-DIS PIC -zzzzzzzzz9.99} (COPAUA0C.cbl:66).
 */
public final class PendingAuthFormat {

    private static final DateTimeFormatter RPT_DATE = DateTimeFormatter.ofPattern("MM/dd/yy");

    private PendingAuthFormat() {
    }

    /** {@code PIC -zzzzzzz9.99} — 12 characters, the list/detail amount. */
    public static String amount12(BigDecimal value) {
        return edited(value, 8);
    }

    /** {@code PIC -zzzz9.99} — 9 characters, the cash/approved/declined amounts. */
    public static String amount9(BigDecimal value) {
        return edited(value, 5);
    }

    /** {@code PIC -zzzzzzzzz9.99} — 14 characters, the CP00 reply amount. */
    public static String amount14(BigDecimal value) {
        return edited(value, 10);
    }

    /** {@code PIC 9(03)} — the approved/declined counts on CPVS. */
    public static String count3(Integer value) {
        int v = value == null ? 0 : value;
        return String.format("%03d", Math.abs(v) % 1000);
    }

    /**
     * A COBOL edited numeric field: a fixed sign position (blank when the value
     * is positive), {@code intDigits} integer positions with leading zeros
     * suppressed to blanks except the last one, a decimal point and 2 decimals.
     * A value wider than the picture is truncated on the left, exactly as the
     * {@code MOVE} into the edited field would.
     */
    private static String edited(BigDecimal value, int intDigits) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        BigDecimal abs = v.abs().setScale(2, RoundingMode.HALF_UP);
        String plain = abs.toPlainString();
        int dot = plain.indexOf('.');
        String digits = plain.substring(0, dot);
        String decimals = plain.substring(dot + 1);
        if (digits.length() > intDigits) {
            digits = digits.substring(digits.length() - intDigits);
        }
        StringBuilder sb = new StringBuilder(intDigits + 4);
        sb.append(v.signum() < 0 ? '-' : ' ');
        for (int i = digits.length(); i < intDigits; i++) {
            sb.append(' ');
        }
        return sb.append(digits).append('.').append(decimals).toString();
    }

    /**
     * {@code PA-AUTH-ORIG-DATE} {@code YYMMDD} rendered {@code MM/DD/YY}
     * (COPAUS0C.cbl:527-533). A short/blank value yields blanks, like the
     * uninitialised {@code WS-AUTH-DATE VALUE '00/00/00'} slots.
     */
    public static String displayDate(String yymmdd) {
        if (yymmdd == null || yymmdd.trim().length() < 6) {
            return "";
        }
        String s = yymmdd.trim();
        return s.substring(2, 4) + "/" + s.substring(4, 6) + "/" + s.substring(0, 2);
    }

    /**
     * {@code PA-AUTH-ORIG-TIME} {@code HHMMSS} rendered {@code HH:MM:SS}
     * (COPAUS0C.cbl:523-525).
     */
    public static String displayTime(String hhmmss) {
        if (hhmmss == null || hhmmss.trim().length() < 6) {
            return "";
        }
        String s = hhmmss.trim();
        return s.substring(0, 2) + ":" + s.substring(2, 4) + ":" + s.substring(4, 6);
    }

    /**
     * {@code PA-CARD-EXPIRY-DATE} {@code YYMM} rendered {@code YY/MM}
     * (COPAUS1C.cbl:337-339).
     */
    public static String displayExpiry(String yymm) {
        if (yymm == null || yymm.trim().length() < 4) {
            return "";
        }
        String s = yymm.trim();
        return s.substring(0, 2) + "/" + s.substring(2, 4);
    }

    /**
     * The fraud report date as the screen and the IMS segment field
     * {@code PA-FRAUD-RPT-DATE PIC X(08)} carry it: {@code MMDDYY} with
     * {@code DATESEP} (COPAUS2C.cbl:101).
     */
    public static String reportDate(LocalDate date) {
        return date == null ? "" : date.format(RPT_DATE);
    }

    /** An 11-digit zero-padded account id, as the maps and messages carry it. */
    public static String acctId11(Long acctId) {
        return String.format("%011d", acctId == null ? 0L : acctId);
    }

    /** A 9-digit zero-padded customer id. */
    public static String custId9(Long custId) {
        return String.format("%09d", custId == null ? 0L : custId);
    }

    /** {@code PIC 9(09)} display of a CICS RESP / RESP2 code in a message. */
    public static String code9(int code) {
        return String.format("%09d", code);
    }
}
