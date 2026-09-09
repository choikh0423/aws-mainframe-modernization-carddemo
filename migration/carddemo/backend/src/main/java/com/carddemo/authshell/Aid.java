package com.carddemo.authshell;

import java.util.Locale;

/**
 * The attention identifier the screen was submitted with, i.e. the {@code EIBAID}
 * the three programs evaluate (COSGN00C.cbl:85-95, COMEN01C.cbl:93-103,
 * COADM01C.cbl:97-107). Only ENTER and PF3 have behaviour; every other key gets
 * CCDA-MSG-INVALID-KEY.
 */
public enum Aid {

    /** DFHENTER. */
    ENTER,
    /** DFHPF3. */
    PF3,
    /** The {@code WHEN OTHER} branch of the AID evaluate. */
    OTHER;

    /** A missing AID is ENTER: the shell submits the map with the enter key. */
    public static Aid of(String value) {
        if (value == null || value.isBlank()) {
            return ENTER;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "ENTER", "DFHENTER" -> ENTER;
            case "PF3", "F3", "DFHPF3" -> PF3;
            default -> OTHER;
        };
    }
}
