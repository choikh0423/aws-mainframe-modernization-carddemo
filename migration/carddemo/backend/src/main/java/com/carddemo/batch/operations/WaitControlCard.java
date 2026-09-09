package com.carddemo.batch.operations;

/**
 * The SYSIN control card COBSWAIT reads (app/cbl/COBSWAIT.cbl:36-37).
 *
 * <p>The COBOL is three statements: {@code ACCEPT PARM-VALUE FROM SYSIN} into a
 * {@code PIC X(8)}, {@code MOVE PARM-VALUE TO MVSWAIT-TIME} which is a
 * {@code PIC 9(8) COMP}, and {@code CALL 'MVSWAIT'} with that binary count of
 * centiseconds. So only the first record of SYSIN is read, only its first eight
 * characters carry the value, and everything after column 8 is commentary - the
 * shipped card is {@code 00003600      VALUE IN CENTISECONDS}
 * (app/jcl/WAITSTEP.jcl:26).
 *
 * <p>An alphanumeric-to-numeric MOVE of a card whose first eight characters are
 * not all digits is undefined on z/OS; here it is rejected (FR-OC-09).
 */
public record WaitControlCard(String value, long centiseconds) {

    /** Width of PARM-VALUE, {@code PIC X(8)} (COBSWAIT.cbl:31). */
    public static final int VALUE_LENGTH = 8;

    /** Largest value MVSWAIT-TIME can hold, {@code PIC 9(8) COMP} (COBSWAIT.cbl:30). */
    public static final long MAX_CENTISECONDS = 99_999_999L;

    /** Centiseconds to milliseconds: the unit MVSWAIT's interval timer counts in. */
    public static final int MILLIS_PER_CENTISECOND = 10;

    /**
     * Reads the wait value the way COBSWAIT does.
     *
     * @param sysin the whole SYSIN stream; only its first record is read
     * @throws IllegalArgumentException if the card's value columns are not eight digits
     */
    public static WaitControlCard parse(String sysin) {
        String firstRecord = sysin == null ? "" : sysin.split("\r?\n", -1)[0];
        String padded = firstRecord.length() < VALUE_LENGTH
                ? firstRecord + " ".repeat(VALUE_LENGTH - firstRecord.length())
                : firstRecord;
        String value = padded.substring(0, VALUE_LENGTH);
        if (!value.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException(
                    "SYSIN wait value is not 8 numeric digits: '" + value + "'");
        }
        return new WaitControlCard(value, Long.parseLong(value));
    }

    public long millis() {
        return centiseconds * MILLIS_PER_CENTISECOND;
    }
}
