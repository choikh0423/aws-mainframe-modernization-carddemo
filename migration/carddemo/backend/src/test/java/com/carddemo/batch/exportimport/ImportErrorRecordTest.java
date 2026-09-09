package com.carddemo.batch.exportimport;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WS-ERROR-RECORD, quirks included: the raw FUNCTION CURRENT-DATE timestamp
 * rather than the export format, and a PIC 9(7) sequence field that silently
 * loses the high-order digits of a longer sequence number.
 */
class ImportErrorRecordTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 9, 9, 19, 13, 41, 0, ZoneOffset.UTC);

    @Test
    void theRecordIsOneHundredAndThirtyTwoBytesLaidOutFieldByField() {
        byte[] record = unknownRecord(42);

        String line = ImportErrorRecord.unknownRecordType(record, NOW);

        assertThat(line).hasSize(132);
        assertThat(line.substring(0, 26)).isEqualTo("20260909191341" + "00" + "+0000" + "     ");
        assertThat(line.charAt(26)).isEqualTo('|');
        assertThat(line.charAt(27)).isEqualTo('Z');
        assertThat(line.charAt(28)).isEqualTo('|');
        assertThat(line.substring(29, 36)).isEqualTo("0000042");
        assertThat(line.charAt(36)).isEqualTo('|');
        assertThat(line.substring(37, 68)).isEqualTo("Unknown record type encountered");
        assertThat(line.substring(68)).isBlank();
    }

    /** The message text is the COBOL literal, verbatim. */
    @Test
    void theMessageIsTheCobolLiteral() {
        assertThat(ImportErrorRecord.UNKNOWN_RECORD_TYPE_MESSAGE)
                .isEqualTo("Unknown record type encountered");
    }

    /** Quirk: PIC 9(9) sequence into PIC 9(7) keeps the low-order seven digits. */
    @Test
    void aSequenceNumberOverSevenDigitsKeepsItsLowOrderDigits() {
        String line = ImportErrorRecord.unknownRecordType(unknownRecord(123456789), NOW);

        assertThat(line.substring(29, 36)).isEqualTo("3456789");
    }

    private static byte[] unknownRecord(long sequenceNumber) {
        byte[] record = ExportRecordCodec.cardRecord(ExportImportFixtures.card(), "2026-09-09 19:13:41.00");
        record[0] = 'Z';
        ExportRecordCodec.putSequenceNumber(record, sequenceNumber);
        return record;
    }
}
