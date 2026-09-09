package com.carddemo.batch.exportimport;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;

import static com.carddemo.batch.exportimport.MainframeFieldCodec.putText;
import static com.carddemo.batch.exportimport.MainframeFieldCodec.putZoned;

/**
 * WS-ERROR-RECORD (CBIMPORT.cbl:152-160), written by 2700-PROCESS-UNKNOWN-RECORD
 * for every record whose type byte matches no WHEN of the EVALUATE.
 *
 * <p>Two quirks are preserved rather than tidied up: the timestamp is the raw
 * {@code FUNCTION CURRENT-DATE} value, not the {@code YYYY-MM-DD HH:MM:SS.00}
 * form the export record carries (FR-I-17); and the {@code PIC 9(7)} sequence
 * field keeps only the low-order seven digits of the {@code PIC 9(9)} export
 * sequence number (FR-I-18).
 */
public final class ImportErrorRecord {

    /** ERR-MESSAGE, CBIMPORT.cbl:432. */
    public static final String UNKNOWN_RECORD_TYPE_MESSAGE = "Unknown record type encountered";

    private ImportErrorRecord() {
    }

    public static String unknownRecordType(byte[] exportRecord, OffsetDateTime now) {
        return build(MainframeTimestamps.currentDateFunction(now),
                ExportRecordCodec.recordType(exportRecord),
                ExportRecordCodec.sequenceNumber(exportRecord),
                UNKNOWN_RECORD_TYPE_MESSAGE);
    }

    static String build(String timestamp, char recordType, long sequenceNumber, String message) {
        byte[] out = new byte[ImportTarget.ERROR.recordLength()];
        Arrays.fill(out, (byte) ' ');
        putText(out, 0, 26, timestamp);
        putText(out, 26, 1, "|");
        putText(out, 27, 1, String.valueOf(recordType));
        putText(out, 28, 1, "|");
        putZoned(out, 29, 7, sequenceNumber);
        putText(out, 36, 1, "|");
        putText(out, 37, 50, message);
        return new String(out, StandardCharsets.ISO_8859_1);
    }
}
