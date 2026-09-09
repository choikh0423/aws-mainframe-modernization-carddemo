package com.carddemo.batch.exportimport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.carddemo.batch.exportimport.ExportStatistics.nineDigits;

/**
 * CBIMPORT's counters and its DISPLAY output (CBIMPORT.cbl:176-193, 449-452,
 * 465-478), including the validation block that always reports success.
 */
public class ImportStatistics {

    private static final Logger log = LoggerFactory.getLogger(ImportStatistics.class);

    private final Map<ImportTarget, Long> counts = new EnumMap<>(ImportTarget.class);
    private final List<String> lines = new ArrayList<>();

    private long recordsRead;
    private long unknownRecordTypes;

    /** 1000-INITIALIZE (CBIMPORT.cbl:176-193). */
    public void starting(String importDate, String importTime) {
        emit("CBIMPORT: Starting Customer Data Import");
        emit("CBIMPORT: Import Date: " + importDate);
        emit("CBIMPORT: Import Time: " + importTime);
    }

    /** WS-TOTAL-RECORDS-READ counts every record, unknown types included. */
    public void recordRead() {
        recordsRead++;
    }

    public void recordWritten(ImportTarget target) {
        counts.merge(target, 1L, Long::sum);
    }

    /** 2700-PROCESS-UNKNOWN-RECORD: the unknown-type counter (CBIMPORT.cbl:427). */
    public void unknownRecordType() {
        unknownRecordTypes++;
    }

    /**
     * 3000-VALIDATE-IMPORT (CBIMPORT.cbl:449-452). Quirk preserved: no
     * validation is performed and success is reported even when error records
     * were written.
     */
    public void validationCompleted() {
        emit("CBIMPORT: Import validation completed");
        emit("CBIMPORT: No validation errors detected");
    }

    /** 4000-FINALIZE (CBIMPORT.cbl:465-478). */
    public void completed() {
        emit("CBIMPORT: Import completed");
        emit("CBIMPORT: Total Records Read: " + nineDigits(recordsRead));
        emit("CBIMPORT: Customers Imported: " + count(ImportTarget.CUSTOMER));
        emit("CBIMPORT: Accounts Imported: " + count(ImportTarget.ACCOUNT));
        emit("CBIMPORT: XRefs Imported: " + count(ImportTarget.XREF));
        emit("CBIMPORT: Transactions Imported: " + count(ImportTarget.TRANSACTION));
        emit("CBIMPORT: Cards Imported: " + count(ImportTarget.CARD));
        emit("CBIMPORT: Errors Written: " + count(ImportTarget.ERROR));
        emit("CBIMPORT: Unknown Record Types: " + nineDigits(unknownRecordTypes));
    }

    public long recordsRead() {
        return recordsRead;
    }

    public long imported(ImportTarget target) {
        return counts.getOrDefault(target, 0L);
    }

    public long unknownRecordTypes() {
        return unknownRecordTypes;
    }

    public List<String> lines() {
        return List.copyOf(lines);
    }

    private String count(ImportTarget target) {
        return nineDigits(imported(target));
    }

    private void emit(String line) {
        lines.add(line);
        log.info(line);
    }
}
