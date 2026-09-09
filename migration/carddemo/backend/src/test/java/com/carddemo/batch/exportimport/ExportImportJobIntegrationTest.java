package com.carddemo.batch.exportimport;

import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.CardRepository;
import com.carddemo.common.repository.CardXrefRepository;
import com.carddemo.common.repository.CustomerRepository;
import com.carddemo.common.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CBEXPORT and CBIMPORT end to end against the seeded database, which is the
 * H2 image of the {@code app/data/ASCII} unloads (50 customers, 50 accounts,
 * 50 cards, 50 cross-references, 300 transactions) - the same fixtures the
 * legacy jobs ran against.
 *
 * <p>The two jobs are tested together because the hand-off file is the contract
 * between them: CBEXPORT's output is CBIMPORT's input, and a round trip is the
 * only assertion that proves the layout is truly byte compatible.
 */
@SpringBatchTest
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-exportimport;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "carddemo.batch.export-import-dir=${java.io.tmpdir}/carddemo-exportimport-test"
})
class ExportImportJobIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    @Qualifier("exportJob")
    private Job exportJob;
    @Autowired
    @Qualifier("importJob")
    private Job importJob;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private CardXrefRepository cardXrefs;
    @Autowired
    private TransactionRepository transactions;
    @Autowired
    private CardRepository cards;
    @Value("${carddemo.batch.export-import-dir}")
    private Path directory;

    @Test
    void cbexportWritesOneFiveHundredByteRecordPerMasterRecordInProgramOrder() throws Exception {
        JobExecution execution = runExport();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions()).extracting(step -> step.getStepName())
                .containsExactly("STEP01", "STEP02");

        byte[] file = Files.readAllBytes(exportFile());
        assertThat(file.length % ExportRecordCodec.RECORD_LENGTH).isZero();
        assertThat(file.length / ExportRecordCodec.RECORD_LENGTH).isEqualTo(seededTotal());

        List<byte[]> records = records(file);
        assertThat(typeRun(records)).containsExactly(
                "C" + customers.count(),
                "A" + accounts.count(),
                "X" + cardXrefs.count(),
                "T" + transactions.count(),
                "D" + cards.count());
    }

    @Test
    void everyExportedRecordCarriesTheRunStampAndItsOwnSequenceNumber() throws Exception {
        runExport();

        List<byte[]> records = records(Files.readAllBytes(exportFile()));
        String timestamp = ExportRecordCodec.timestamp(records.get(0));

        assertThat(timestamp).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.00");
        for (int i = 0; i < records.size(); i++) {
            byte[] record = records.get(i);
            assertThat(ExportRecordCodec.timestamp(record)).isEqualTo(timestamp);
            assertThat(ExportRecordCodec.sequenceNumber(record)).isEqualTo(i + 1L);
            assertThat(text(record, 31, 4)).isEqualTo("0001");
            assertThat(text(record, 35, 5)).isEqualTo("NORTH");
        }
    }

    /** STEP01 is the migrated IDCAMS DELETE/DEFINE: a rerun replaces the file. */
    @Test
    void rerunningCbexportReplacesTheFileRatherThanAppendingToIt() throws Exception {
        runExport();
        long firstRun = Files.size(exportFile());

        runExport();

        assertThat(Files.size(exportFile())).isEqualTo(firstRun);
    }

    @Test
    void cbimportSplitsTheExportFileIntoItsSixNormalisedFiles() throws Exception {
        runExport();

        JobExecution execution = runImport();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions()).extracting(step -> step.getStepName())
                .containsExactly("STEP01");

        assertThat(lines(ImportTarget.CUSTOMER)).hasSize((int) customers.count())
                .allMatch(line -> line.length() == ImportTarget.CUSTOMER.recordLength());
        assertThat(lines(ImportTarget.ACCOUNT)).hasSize((int) accounts.count())
                .allMatch(line -> line.length() == ImportTarget.ACCOUNT.recordLength());
        assertThat(lines(ImportTarget.XREF)).hasSize((int) cardXrefs.count())
                .allMatch(line -> line.length() == ImportTarget.XREF.recordLength());
        assertThat(lines(ImportTarget.TRANSACTION)).hasSize((int) transactions.count())
                .allMatch(line -> line.length() == ImportTarget.TRANSACTION.recordLength());
        assertThat(lines(ImportTarget.CARD)).hasSize((int) cards.count())
                .allMatch(line -> line.length() == ImportTarget.CARD.recordLength());
        assertThat(lines(ImportTarget.ERROR)).isEmpty();
    }

    /** The round trip: what comes out of CBIMPORT is what went into CBEXPORT. */
    @Test
    void aCustomerSurvivesTheRoundTripUnchanged() throws Exception {
        runExport();
        runImport();

        var customer = customers.findAll().iterator().next();
        assertThat(lines(ImportTarget.CUSTOMER)).anySatisfy(line -> {
            assertThat(line).startsWith(String.format("%09d", customer.getCustId()));
            assertThat(line.substring(9, 34).stripTrailing()).isEqualTo(customer.getFirstName());
            assertThat(line.substring(59, 84).stripTrailing()).isEqualTo(customer.getLastName());
            assertThat(line.substring(279, 288)).isEqualTo(String.format("%09d", customer.getSsn()));
            assertThat(line.substring(329, 332))
                    .isEqualTo(String.format("%03d", customer.getFicoCreditScore()));
        });
    }

    /** WHEN OTHER: one error record, processing continues, the run still succeeds. */
    @Test
    void anUnknownRecordTypeProducesAnErrorRecordAndDoesNotStopTheRun() throws Exception {
        runExport();
        corruptFirstRecordType();

        JobExecution execution = runImport();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(lines(ImportTarget.CUSTOMER)).hasSize((int) customers.count() - 1);
        assertThat(lines(ImportTarget.ERROR)).singleElement().satisfies(line -> {
            assertThat(line).hasSize(ImportTarget.ERROR.recordLength());
            assertThat(line.charAt(27)).isEqualTo('Z');
            assertThat(line.substring(29, 36)).isEqualTo("0000001");
            assertThat(line.substring(37, 68)).isEqualTo(ImportErrorRecord.UNKNOWN_RECORD_TYPE_MESSAGE);
        });
        assertThat(displayLines(execution, ImportJobConfiguration.DISPLAY_LINES_KEY))
                .contains("CBIMPORT: Total Records Read: " + String.format("%09d", seededTotal()))
                .contains("CBIMPORT: Errors Written: 000000001")
                .contains("CBIMPORT: Unknown Record Types: 000000001")
                // The quirk: validation always reports success (FR-I-21).
                .contains("CBIMPORT: No validation errors detected");
    }

    @Test
    void bothJobsLogTheCobolDisplayLines() throws Exception {
        JobExecution export = runExport();
        JobExecution imported = runImport();

        assertThat(displayLines(export, ExportJobConfiguration.DISPLAY_LINES_KEY))
                .contains("CBEXPORT: Processing customer records")
                .contains("CBEXPORT: Processing card records")
                .contains("CBEXPORT: Export completed")
                .contains("CBEXPORT: Customers Exported: " + String.format("%09d", customers.count()))
                .contains("CBEXPORT: Transactions Exported: " + String.format("%09d", transactions.count()))
                .contains("CBEXPORT: Total Records Exported: " + String.format("%09d", seededTotal()));
        assertThat(displayLines(imported, ImportJobConfiguration.DISPLAY_LINES_KEY))
                .contains("CBIMPORT: Starting Customer Data Import")
                .contains("CBIMPORT: Import completed")
                .contains("CBIMPORT: Cards Imported: " + String.format("%09d", cards.count()));
    }

    /** A missing hand-off file is the legacy OPEN failure: message, then abend. */
    @Test
    void cbimportAbendsWhenTheExportFileIsMissing() throws Exception {
        Files.createDirectories(directory);
        Files.deleteIfExists(exportFile());

        JobExecution execution = runImport();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(failureMessages(execution))
                .anyMatch(message -> message.contains("ERROR: Cannot open EXPORT-INPUT, Status: 35"));
    }

    /** The seeded ASCII unloads, counted rather than hard coded. */
    private long seededTotal() {
        return customers.count() + accounts.count() + cardXrefs.count()
                + transactions.count() + cards.count();
    }

    private JobExecution runExport() throws Exception {
        jobLauncherTestUtils.setJob(exportJob);
        return jobLauncherTestUtils.launchJob(uniqueParameters());
    }

    private JobExecution runImport() throws Exception {
        jobLauncherTestUtils.setJob(importJob);
        return jobLauncherTestUtils.launchJob(uniqueParameters());
    }

    private static JobParameters uniqueParameters() {
        return new JobParametersBuilder().addString("run", UUID.randomUUID().toString()).toJobParameters();
    }

    private Path exportFile() {
        return directory.resolve(ExportJobConfiguration.EXPORT_FILE_NAME);
    }

    private void corruptFirstRecordType() throws IOException {
        byte[] file = Files.readAllBytes(exportFile());
        file[0] = 'Z';
        Files.write(exportFile(), file);
    }

    private List<String> lines(ImportTarget target) throws IOException {
        return Files.readAllLines(directory.resolve(target.fileName()), StandardCharsets.ISO_8859_1);
    }

    /** Every message in the failure chain: the abend may arrive wrapped. */
    private static List<String> failureMessages(JobExecution execution) {
        List<String> messages = new java.util.ArrayList<>();
        for (Throwable failure : execution.getAllFailureExceptions()) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                messages.add(String.valueOf(cause.getMessage()));
                if (cause.getCause() == cause) {
                    break;
                }
            }
        }
        return messages;
    }

    private static List<String> displayLines(JobExecution execution, String key) {
        return List.of(execution.getExecutionContext().getString(key).split("\n"));
    }

    private static List<byte[]> records(byte[] file) {
        return java.util.stream.IntStream.range(0, file.length / ExportRecordCodec.RECORD_LENGTH)
                .mapToObj(i -> java.util.Arrays.copyOfRange(file,
                        i * ExportRecordCodec.RECORD_LENGTH, (i + 1) * ExportRecordCodec.RECORD_LENGTH))
                .toList();
    }

    /** The record types in file order, run-length encoded: C50, A50, X50, T300, D50. */
    private static List<String> typeRun(List<byte[]> records) {
        List<String> runs = new java.util.ArrayList<>();
        char current = 0;
        int count = 0;
        for (byte[] record : records) {
            char type = ExportRecordCodec.recordType(record);
            if (type != current) {
                if (count > 0) {
                    runs.add("" + current + count);
                }
                current = type;
                count = 0;
            }
            count++;
        }
        runs.add("" + current + count);
        return runs;
    }

    private static String text(byte[] record, int offset, int length) {
        return new String(record, offset, length, StandardCharsets.ISO_8859_1);
    }
}
