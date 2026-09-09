package com.carddemo.batch.exportimport;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The two timestamp renderings this stream needs, both taken literally from the
 * COBOL rather than normalised into one format - CBEXPORT stamps records with a
 * hand-assembled {@code YYYY-MM-DD HH:MM:SS.00} string while CBIMPORT stamps
 * error records with the raw {@code FUNCTION CURRENT-DATE} value (FR-I-17).
 */
public final class MainframeTimestamps {

    private static final DateTimeFormatter EXPORT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter EXPORT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter CURRENT_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSS");

    private MainframeTimestamps() {
    }

    /** WS-EXPORT-DATE, {@code YYYY-MM-DD} (CBEXPORT.cbl:175-181). */
    public static String exportDate(OffsetDateTime now) {
        return EXPORT_DATE.format(now);
    }

    /** WS-EXPORT-TIME, {@code HH:MM:SS} (CBEXPORT.cbl:183-188). */
    public static String exportTime(OffsetDateTime now) {
        return EXPORT_TIME.format(now);
    }

    /**
     * WS-FORMATTED-TIMESTAMP (CBEXPORT.cbl:190-195): the date, a space, the time
     * and the literal {@code .00} - the hundredths CBEXPORT reads are never used.
     * 22 characters, which a MOVE into the X(26) EXPORT-TIMESTAMP pads with four
     * spaces.
     */
    public static String exportTimestamp(OffsetDateTime now) {
        return exportDate(now) + " " + exportTime(now) + ".00";
    }

    /**
     * COBOL's {@code FUNCTION CURRENT-DATE}: {@code YYYYMMDDhhmmssnn} followed by
     * the offset from GMT as {@code ±hhmm} - 21 characters.
     */
    public static String currentDateFunction(OffsetDateTime now) {
        int offsetMinutes = now.getOffset().getTotalSeconds() / 60;
        String sign = offsetMinutes < 0 ? "-" : "+";
        int absolute = Math.abs(offsetMinutes);
        return CURRENT_DATE.format(now) + String.format("%s%02d%02d", sign, absolute / 60, absolute % 60);
    }
}
