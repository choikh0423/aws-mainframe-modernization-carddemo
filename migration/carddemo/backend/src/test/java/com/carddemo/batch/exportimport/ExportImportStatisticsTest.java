package com.carddemo.batch.exportimport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The job log. Every line is a COBOL DISPLAY literal, so these assertions are
 * the fidelity check on the message text and on the nine-digit counters.
 */
class ExportImportStatisticsTest {

    @Test
    void cbexportLogsItsGroupsAndItsTotals() {
        ExportStatistics statistics = new ExportStatistics();

        statistics.starting("2026-09-09", "19:13:41");
        statistics.startGroup(ExportRecordCodec.TYPE_CUSTOMER);
        statistics.recordExported(ExportRecordCodec.TYPE_CUSTOMER);
        statistics.recordExported(ExportRecordCodec.TYPE_CUSTOMER);
        statistics.endGroup(ExportRecordCodec.TYPE_CUSTOMER);
        statistics.startGroup(ExportRecordCodec.TYPE_CARD);
        statistics.recordExported(ExportRecordCodec.TYPE_CARD);
        statistics.endGroup(ExportRecordCodec.TYPE_CARD);
        statistics.completed();

        assertThat(statistics.lines()).containsExactly(
                "CBEXPORT: Starting Customer Data Export",
                "CBEXPORT: Export Date: 2026-09-09",
                "CBEXPORT: Export Time: 19:13:41",
                "CBEXPORT: Processing customer records",
                "CBEXPORT: Customers exported: 000000002",
                "CBEXPORT: Processing card records",
                "CBEXPORT: Cards exported: 000000001",
                "CBEXPORT: Export completed",
                "CBEXPORT: Customers Exported: 000000002",
                "CBEXPORT: Accounts Exported: 000000000",
                "CBEXPORT: XRefs Exported: 000000000",
                "CBEXPORT: Transactions Exported: 000000000",
                "CBEXPORT: Cards Exported: 000000001",
                "CBEXPORT: Total Records Exported: 000000003");
        assertThat(statistics.totalExported()).isEqualTo(3);
        assertThat(statistics.exported(ExportRecordCodec.TYPE_ACCOUNT)).isZero();
    }

    /**
     * Quirk: 3000-VALIDATE-IMPORT reports success unconditionally, so a run that
     * wrote error records still logs 'No validation errors detected'.
     */
    @Test
    void cbimportReportsValidationSuccessEvenAfterWritingErrorRecords() {
        ImportStatistics statistics = new ImportStatistics();

        statistics.starting("2026-09-09", "19:13:41");
        statistics.recordRead();
        statistics.recordRead();
        statistics.recordWritten(ImportTarget.CUSTOMER);
        statistics.unknownRecordType();
        statistics.recordWritten(ImportTarget.ERROR);
        statistics.validationCompleted();
        statistics.completed();

        assertThat(statistics.lines()).containsExactly(
                "CBIMPORT: Starting Customer Data Import",
                "CBIMPORT: Import Date: 2026-09-09",
                "CBIMPORT: Import Time: 19:13:41",
                "CBIMPORT: Import validation completed",
                "CBIMPORT: No validation errors detected",
                "CBIMPORT: Import completed",
                "CBIMPORT: Total Records Read: 000000002",
                "CBIMPORT: Customers Imported: 000000001",
                "CBIMPORT: Accounts Imported: 000000000",
                "CBIMPORT: XRefs Imported: 000000000",
                "CBIMPORT: Transactions Imported: 000000000",
                "CBIMPORT: Cards Imported: 000000000",
                "CBIMPORT: Errors Written: 000000001",
                "CBIMPORT: Unknown Record Types: 000000001");
        assertThat(statistics.recordsRead()).isEqualTo(2);
        assertThat(statistics.unknownRecordTypes()).isEqualTo(1);
    }
}
