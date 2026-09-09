package com.carddemo.batch.exportimport;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.carddemo.batch.exportimport.ExportRecordCodec.DATA;
import static com.carddemo.batch.exportimport.MainframeFieldCodec.getBinary;
import static com.carddemo.batch.exportimport.MainframeFieldCodec.getBinaryDecimal;
import static com.carddemo.batch.exportimport.MainframeFieldCodec.getPacked;
import static com.carddemo.batch.exportimport.MainframeFieldCodec.getText;
import static com.carddemo.batch.exportimport.MainframeFieldCodec.getZoned;
import static com.carddemo.batch.exportimport.MainframeFieldCodec.getZonedSigned;
import static com.carddemo.batch.exportimport.MainframeFieldCodec.putText;
import static com.carddemo.batch.exportimport.MainframeFieldCodec.putZoned;
import static com.carddemo.batch.exportimport.MainframeFieldCodec.putZonedSigned;

/**
 * Turns a 500-byte export record back into the normalised record its target
 * file holds - CVCUS01Y (500), CVACT01Y (300), CVACT03Y (50), CVTRA05Y (350),
 * CVACT02Y (150) - exactly as CBIMPORT's 2300/2400/2500/2600/2650 paragraphs do.
 *
 * <p>The target copybooks are DISPLAY throughout, so the binary and packed
 * fields of the export layout are decoded here and re-emitted zero padded, with
 * signed amounts carrying a trailing overpunch. That makes an output file byte
 * compatible with the corresponding {@code app/data/ASCII} unload.
 */
public final class ImportRecordCodec {

    private ImportRecordCodec() {
    }

    /** EVALUATE EXPORT-REC-TYPE (CBIMPORT.cbl:272-285); null when no WHEN matches. */
    public static ImportedRecord decode(byte[] record) {
        return switch (ExportRecordCodec.recordType(record)) {
            case ExportRecordCodec.TYPE_CUSTOMER -> new ImportedRecord(ImportTarget.CUSTOMER, customerLine(record));
            case ExportRecordCodec.TYPE_ACCOUNT -> new ImportedRecord(ImportTarget.ACCOUNT, accountLine(record));
            case ExportRecordCodec.TYPE_XREF -> new ImportedRecord(ImportTarget.XREF, cardXrefLine(record));
            case ExportRecordCodec.TYPE_TRANSACTION ->
                    new ImportedRecord(ImportTarget.TRANSACTION, transactionLine(record));
            case ExportRecordCodec.TYPE_CARD -> new ImportedRecord(ImportTarget.CARD, cardLine(record));
            default -> null;
        };
    }

    /** 2300-PROCESS-CUSTOMER-RECORD (CBIMPORT.cbl:293-312) into CVCUS01Y. */
    public static String customerLine(byte[] record) {
        byte[] out = blank(ImportTarget.CUSTOMER);
        putZoned(out, 0, 9, getBinary(record, DATA, 4));
        putText(out, 9, 25, getText(record, DATA + 4, 25));
        putText(out, 34, 25, getText(record, DATA + 29, 25));
        putText(out, 59, 25, getText(record, DATA + 54, 25));
        putText(out, 84, 50, getText(record, DATA + 79, 50));
        putText(out, 134, 50, getText(record, DATA + 129, 50));
        putText(out, 184, 50, getText(record, DATA + 179, 50));
        putText(out, 234, 2, getText(record, DATA + 229, 2));
        putText(out, 236, 3, getText(record, DATA + 231, 3));
        putText(out, 239, 10, getText(record, DATA + 234, 10));
        putText(out, 249, 15, getText(record, DATA + 244, 15));
        putText(out, 264, 15, getText(record, DATA + 259, 15));
        putZoned(out, 279, 9, getZoned(record, DATA + 274, 9));
        putText(out, 288, 20, getText(record, DATA + 283, 20));
        putText(out, 308, 10, getText(record, DATA + 303, 10));
        putText(out, 318, 10, getText(record, DATA + 313, 10));
        putText(out, 328, 1, getText(record, DATA + 323, 1));
        putZoned(out, 329, 3, getPacked(record, DATA + 324, 2, 0).longValueExact());
        return line(out);
    }

    /** 2400-PROCESS-ACCOUNT-RECORD (CBIMPORT.cbl:328-341) into CVACT01Y. */
    public static String accountLine(byte[] record) {
        byte[] out = blank(ImportTarget.ACCOUNT);
        putZoned(out, 0, 11, getZoned(record, DATA, 11));
        putText(out, 11, 1, getText(record, DATA + 11, 1));
        putZonedSigned(out, 12, 12, getPacked(record, DATA + 12, 7, 2), 2);
        putZonedSigned(out, 24, 12, getZonedSigned(record, DATA + 19, 12, 2), 2);
        putZonedSigned(out, 36, 12, getPacked(record, DATA + 31, 7, 2), 2);
        putText(out, 48, 10, getText(record, DATA + 38, 10));
        putText(out, 58, 10, getText(record, DATA + 48, 10));
        putText(out, 68, 10, getText(record, DATA + 58, 10));
        putZonedSigned(out, 78, 12, getZonedSigned(record, DATA + 68, 12, 2), 2);
        putZonedSigned(out, 90, 12, getBinaryDecimal(record, DATA + 80, 8, 2), 2);
        putText(out, 102, 10, getText(record, DATA + 88, 10));
        putText(out, 112, 10, getText(record, DATA + 98, 10));
        return line(out);
    }

    /** 2500-PROCESS-XREF-RECORD (CBIMPORT.cbl:357-361) into CVACT03Y. */
    public static String cardXrefLine(byte[] record) {
        byte[] out = blank(ImportTarget.XREF);
        putText(out, 0, 16, getText(record, DATA, 16));
        putZoned(out, 16, 9, getZoned(record, DATA + 16, 9));
        putZoned(out, 25, 11, getBinary(record, DATA + 25, 8));
        return line(out);
    }

    /** 2600-PROCESS-TRAN-RECORD (CBIMPORT.cbl:377-391) into CVTRA05Y. */
    public static String transactionLine(byte[] record) {
        byte[] out = blank(ImportTarget.TRANSACTION);
        putText(out, 0, 16, getText(record, DATA, 16));
        putText(out, 16, 2, getText(record, DATA + 16, 2));
        putZoned(out, 18, 4, getZoned(record, DATA + 18, 4));
        putText(out, 22, 10, getText(record, DATA + 22, 10));
        putText(out, 32, 100, getText(record, DATA + 32, 100));
        putZonedSigned(out, 132, 11, getPacked(record, DATA + 132, 6, 2), 2);
        putZoned(out, 143, 9, getBinary(record, DATA + 138, 4));
        putText(out, 152, 50, getText(record, DATA + 142, 50));
        putText(out, 202, 50, getText(record, DATA + 192, 50));
        putText(out, 252, 10, getText(record, DATA + 242, 10));
        putText(out, 262, 16, getText(record, DATA + 252, 16));
        putText(out, 278, 26, getText(record, DATA + 268, 26));
        putText(out, 304, 26, getText(record, DATA + 294, 26));
        return line(out);
    }

    /** 2650-PROCESS-CARD-RECORD (CBIMPORT.cbl:407-414) into CVACT02Y. */
    public static String cardLine(byte[] record) {
        byte[] out = blank(ImportTarget.CARD);
        putText(out, 0, 16, getText(record, DATA, 16));
        putZoned(out, 16, 11, getBinary(record, DATA + 16, 8));
        putZoned(out, 27, 3, getBinary(record, DATA + 24, 2));
        putText(out, 30, 50, getText(record, DATA + 26, 50));
        putText(out, 80, 10, getText(record, DATA + 76, 10));
        putText(out, 90, 1, getText(record, DATA + 86, 1));
        return line(out);
    }

    private static byte[] blank(ImportTarget target) {
        byte[] out = new byte[target.recordLength()];
        Arrays.fill(out, (byte) ' ');
        return out;
    }

    private static String line(byte[] out) {
        return new String(out, StandardCharsets.ISO_8859_1);
    }
}
