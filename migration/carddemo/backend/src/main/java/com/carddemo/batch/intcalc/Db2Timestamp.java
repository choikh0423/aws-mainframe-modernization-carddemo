package com.carddemo.batch.intcalc;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * {@code Z-GET-DB2-FORMAT-TIMESTAMP} (CBACT04C.cbl:613-626): the current date and
 * time as {@code YYYY-MM-DD-HH.MM.SS.hh0000} — 26 characters, hundredths of a
 * second from {@code COB-MIL} followed by the literal {@code '0000'}.
 *
 * <p>This is the only clock read in the program. The business run date is the
 * PARM (job parameter {@code run.date}) and is never taken from here.
 */
final class Db2Timestamp {

    private Db2Timestamp() {
    }

    static String now(Clock clock) {
        LocalDateTime ts = LocalDateTime.now(clock);
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000",
                ts.getYear(), ts.getMonthValue(), ts.getDayOfMonth(),
                ts.getHour(), ts.getMinute(), ts.getSecond(),
                ts.getNano() / 10_000_000);
    }
}
