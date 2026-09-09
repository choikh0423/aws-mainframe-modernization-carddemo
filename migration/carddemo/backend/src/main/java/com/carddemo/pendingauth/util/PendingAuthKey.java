package com.carddemo.pendingauth.util;

import com.carddemo.common.domain.PendingAuthDetailId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The IMS child sequence field {@code PAUT9CTS}
 * ({@code PA-AUTH-DATE-9C PIC S9(05) COMP-3} + {@code PA-AUTH-TIME-9C PIC
 * S9(09) COMP-3}, CIPAUDTY.cpy) and the 9s-complement arithmetic COPAUA0C and
 * CBPAUP0C do on it.
 *
 * <p>On the mainframe the two packed fields are carried around as the 8-byte
 * group {@code PA-AUTHORIZATION-KEY}, which the COMMAREA holds as {@code X(08)}
 * (COPAUS0C.cbl:120-127). Off the mainframe there is no packed representation,
 * so the key travels as the 14 digits {@code DDDDDTTTTTTTTT} — the same two
 * fields, zero padded, in the same order, so string order still equals key
 * order (boundary decision BD-2).
 */
public final class PendingAuthKey {

    /** {@code COMPUTE WS-AUTH-DATE = 99999 - PA-AUTH-DATE-9C} (CBPAUP0C.cbl:282). */
    public static final int DATE_COMPLEMENT = 99999;

    /** {@code COMPUTE WS-AUTH-TIME = 999999999 - PA-AUTH-TIME-9C} (COPAUS2C.cbl:104). */
    public static final long TIME_COMPLEMENT = 999999999L;

    private static final DateTimeFormatter YYDDD = DateTimeFormatter.ofPattern("yyDDD");

    private PendingAuthKey() {
    }

    /** The 14-digit external form of a detail record's key. */
    public static String of(int authDate9c, long authTime9c) {
        return String.format("%05d%09d", authDate9c, authTime9c);
    }

    /** The 14-digit external form of a persisted detail id. */
    public static String of(PendingAuthDetailId id) {
        return of(id.getPaAuthDate9c(), id.getPaAuthTime9c());
    }

    /** True when the value is a usable 14-digit key. */
    public static boolean isValid(String key) {
        return key != null && key.trim().length() == 14 && key.trim().chars().allMatch(Character::isDigit);
    }

    /** The inverted date part of a 14-digit key. */
    public static int date9c(String key) {
        return Integer.parseInt(key.trim().substring(0, 5));
    }

    /** The inverted time part of a 14-digit key. */
    public static long time9c(String key) {
        return Long.parseLong(key.trim().substring(5));
    }

    /** {@code 99999 - yyddd}: the stored, inverted authorization date. */
    public static int invertDate(int yyddd) {
        return DATE_COMPLEMENT - yyddd;
    }

    /** {@code 999999999 - hhmmssmmm}: the stored, inverted authorization time. */
    public static long invertTime(long timeWithMillis) {
        return TIME_COMPLEMENT - timeWithMillis;
    }

    /**
     * The key COPAUA0C's 8500-INSERT-AUTH builds from the current CICS clock
     * (COPAUA0C.cbl:860-875): {@code YYDDD} of the date and
     * {@code HHMMSS * 1000 + milliseconds} of the time, both complemented.
     */
    public static PendingAuthDetailId newKey(Long acctId, LocalDateTime now) {
        int yyddd = Integer.parseInt(now.toLocalDate().format(YYDDD));
        long hhmmss = now.getHour() * 10000L + now.getMinute() * 100L + now.getSecond();
        long withMillis = hhmmss * 1000L + now.getNano() / 1_000_000L;
        return new PendingAuthDetailId(acctId, invertDate(yyddd), invertTime(withMillis));
    }

    /** The {@code YYDDD} form CBPAUP0C compares against (CBPAUP0C.cbl:282-284). */
    public static int yyddd(LocalDate date) {
        return Integer.parseInt(date.format(YYDDD));
    }
}
