package com.carddemo.batch.exportimport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CBEXPORT's counters and its DISPLAY output, reproduced line for line
 * (CBEXPORT.cbl:163-169, 245-255, 314-324, 378-388, 433-443, 498-508, 563-573).
 *
 * <p>A DISPLAY of a literal followed by a {@code PIC 9(09)} counter emits the
 * counter zero padded to nine digits with no separator, so the lines here are
 * assembled the same way. Every line is both kept for assertion and logged,
 * because the operator's job log is the only output CBEXPORT has besides the
 * export file itself.
 */
public class ExportStatistics {

    private static final Logger log = LoggerFactory.getLogger(ExportStatistics.class);

    private final Map<Character, Long> counts = new LinkedHashMap<>();
    private final List<String> lines = new ArrayList<>();

    /** 1000-INITIALIZE (CBEXPORT.cbl:163-169). */
    public void starting(String exportDate, String exportTime) {
        emit("CBEXPORT: Starting Customer Data Export");
        emit("CBEXPORT: Export Date: " + exportDate);
        emit("CBEXPORT: Export Time: " + exportTime);
    }

    /** The 'Processing ... records' line each export paragraph opens with. */
    public void startGroup(char recordType) {
        counts.putIfAbsent(recordType, 0L);
        emit("CBEXPORT: Processing " + processingLabel(recordType) + " records");
    }

    public void recordExported(char recordType) {
        counts.merge(recordType, 1L, Long::sum);
    }

    /** The '<group> exported: nnnnnnnnn' line each export paragraph closes with. */
    public void endGroup(char recordType) {
        emit("CBEXPORT: " + exportedLabel(recordType) + " exported: " + count(recordType));
    }

    /** 6000-FINALIZE (CBEXPORT.cbl:563-573). */
    public void completed() {
        emit("CBEXPORT: Export completed");
        emit("CBEXPORT: Customers Exported: " + count(ExportRecordCodec.TYPE_CUSTOMER));
        emit("CBEXPORT: Accounts Exported: " + count(ExportRecordCodec.TYPE_ACCOUNT));
        emit("CBEXPORT: XRefs Exported: " + count(ExportRecordCodec.TYPE_XREF));
        emit("CBEXPORT: Transactions Exported: " + count(ExportRecordCodec.TYPE_TRANSACTION));
        emit("CBEXPORT: Cards Exported: " + count(ExportRecordCodec.TYPE_CARD));
        emit("CBEXPORT: Total Records Exported: " + nineDigits(totalExported()));
    }

    public long totalExported() {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    public long exported(char recordType) {
        return counts.getOrDefault(recordType, 0L);
    }

    /** The DISPLAY lines produced so far, in order. */
    public List<String> lines() {
        return List.copyOf(lines);
    }

    private String count(char recordType) {
        return nineDigits(exported(recordType));
    }

    static String nineDigits(long value) {
        return String.format("%09d", value);
    }

    private static String processingLabel(char recordType) {
        return switch (recordType) {
            case ExportRecordCodec.TYPE_CUSTOMER -> "customer";
            case ExportRecordCodec.TYPE_ACCOUNT -> "account";
            case ExportRecordCodec.TYPE_XREF -> "cross-reference";
            case ExportRecordCodec.TYPE_TRANSACTION -> "transaction";
            case ExportRecordCodec.TYPE_CARD -> "card";
            default -> throw new IllegalArgumentException("Unsupported export record type: " + recordType);
        };
    }

    private static String exportedLabel(char recordType) {
        return switch (recordType) {
            case ExportRecordCodec.TYPE_CUSTOMER -> "Customers";
            case ExportRecordCodec.TYPE_ACCOUNT -> "Accounts";
            case ExportRecordCodec.TYPE_XREF -> "Cross-references";
            case ExportRecordCodec.TYPE_TRANSACTION -> "Transactions";
            case ExportRecordCodec.TYPE_CARD -> "Cards";
            default -> throw new IllegalArgumentException("Unsupported export record type: " + recordType);
        };
    }

    private void emit(String line) {
        lines.add(line);
        log.info(line);
    }
}
