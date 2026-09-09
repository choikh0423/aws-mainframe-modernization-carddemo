package com.carddemo.batch.posttran;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * CBTRN02C's {@code Z-GET-DB2-FORMAT-TIMESTAMP} (CBTRN02C.cbl:692-705).
 *
 * <p>The paragraph renders {@code FUNCTION CURRENT-DATE} into the {@code X(26)}
 * DB2 timestamp layout declared at CBTRN02C.cbl:159-174:
 * {@code YYYY-MM-DD-HH.MM.SS.hh0000}, where {@code hh} is the hundredths of a
 * second COBOL supplies in {@code COB-MIL} and {@code 0000} is the literal the
 * program moves into {@code DB2-REST}.
 */
@Component
public class Db2TimestampFormatter {

    private final Clock clock;

    public Db2TimestampFormatter() {
        this(Clock.systemDefaultZone());
    }

    Db2TimestampFormatter(Clock clock) {
        this.clock = clock;
    }

    /** The processing timestamp moved into {@code TRAN-PROC-TS}. */
    public String now() {
        return format(LocalDateTime.now(clock));
    }

    static String format(LocalDateTime timestamp) {
        int hundredths = timestamp.getNano() / 10_000_000;
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000",
                timestamp.getYear(), timestamp.getMonthValue(), timestamp.getDayOfMonth(),
                timestamp.getHour(), timestamp.getMinute(), timestamp.getSecond(), hundredths);
    }
}
