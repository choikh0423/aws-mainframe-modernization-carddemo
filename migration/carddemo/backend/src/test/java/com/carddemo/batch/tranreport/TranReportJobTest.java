package com.carddemo.batch.tranreport;

import com.carddemo.common.batch.AbendException;
import com.carddemo.common.domain.TransactionRecord;
import com.carddemo.common.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TRANREPT end to end, against its own in-memory database so that replacing the
 * contents of {@code transactions} does not disturb the shared seeded one.
 *
 * <p>The parity assertion is byte for byte: the report the job writes from the
 * fixture must equal the report the real CBTRN03C wrote from the same fixture
 * (see {@link TransactFixture}).
 */
@SpringBatchTest
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-tranreport;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "carddemo.batch.output-dir=${java.io.tmpdir}/carddemo-tranreport-test"
})
class TranReportJobTest {

    private static final String START = "2022-07-01";
    private static final String END = "2022-07-06";

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    // The context holds several jobs, so the one under test is named explicitly.
    @Autowired
    @Qualifier("tranReportJob")
    private Job tranReportJob;
    @Autowired
    private TransactionRepository transactions;
    @Autowired
    private TransactionReportLauncher launcher;

    @BeforeEach
    void loadTransactFixture() {
        jobLauncherTestUtils.setJob(tranReportJob);
        transactions.deleteAll();
        transactions.saveAll(TransactFixture.rows());
    }

    @Test
    void runsTheThreeJclStepsAndReproducesTheLegacyReport() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(parameters("Monthly", START, END));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions())
                .extracting(step -> step.getStepName())
                .containsExactly("STEP05R", "STEP05RSORT", "STEP10R");

        assertThat(lines(execution, TranReportFiles.BACKUP_FILE)).hasSize(300);
        assertThat(lines(execution, TranReportFiles.EXTRACT_FILE)).hasSize(180);
        assertThat(lines(execution, TranReportFiles.REPORT_FILE))
                .containsExactlyElementsOf(TransactFixture.goldenReport());
    }

    /** The date range is a job parameter: a narrower one reports fewer cards. */
    @Test
    void reportsOnlyTheRequestedDateRange() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(parameters("Custom", "2022-07-02", "2022-07-03"));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(lines(execution, TranReportFiles.EXTRACT_FILE)).hasSize(60);
        List<String> report = lines(execution, TranReportFiles.REPORT_FILE);
        assertThat(report.get(0)).contains("Date Range: 2022-07-02 to 2022-07-03");
        assertThat(report).allMatch(line -> line.length() == 133);
    }

    /** The three parameters CR00 supplies are all required. */
    @Test
    void refusesToStartWithoutTheReportTypeAndDateRange() {
        assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString(TranReportJobConfiguration.PARAM_START_DATE, START)
                .toJobParameters()))
                .isInstanceOf(JobParametersInvalidException.class);
    }

    /** A card with no CARDXREF record abends CBTRN03C, so STEP10R fails. */
    @Test
    void failsTheReportStepWhenALookupMisses() throws Exception {
        TransactionRecord orphan = TransactFixture.row(TransactFixture.images().get(0));
        orphan.setId("9999999999999999");
        orphan.setCardNum("4999999999999999");
        transactions.save(orphan);

        JobExecution execution = jobLauncherTestUtils.launchJob(parameters("Monthly", START, END));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getStepExecutions())
                .filteredOn(step -> step.getStepName().equals("STEP10R"))
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
                    assertThat(step.getFailureExceptions()).first().isInstanceOf(AbendException.class);
                    assertThat(step.getFailureExceptions().get(0).getMessage())
                            .contains("ABEND-CODE=0999")
                            .contains("ABEND-CULPRIT=CBTRN03C")
                            .contains("INVALID CARD NUMBER : 4999999999999999");
                });
    }

    /**
     * The launch port S-07's CR00 screen calls instead of writing JCL to the
     * internal reader: same three values, and it reports where the report went.
     */
    @Test
    void launchesTheJobThroughTheReportLaunchPort() throws Exception {
        TransactionReportSubmission submission = launcher.submit("Yearly", START, END);

        assertThat(submission.status()).isEqualTo(BatchStatus.COMPLETED.name());
        assertThat(Files.readAllLines(Path.of(submission.reportFile()), StandardCharsets.UTF_8))
                .containsExactlyElementsOf(TransactFixture.goldenReport());
    }

    /** Each submission is a new job instance, as each reader submission was. */
    @Test
    void submitsANewJobInstanceForARangeAlreadyReported() throws Exception {
        TransactionReportSubmission first = launcher.submit("Monthly", START, END);
        TransactionReportSubmission second = launcher.submit("Monthly", START, END);

        assertThat(second.jobExecutionId()).isNotEqualTo(first.jobExecutionId());
        assertThat(second.status()).isEqualTo(BatchStatus.COMPLETED.name());
        assertThat(second.reportFile()).isNotEqualTo(first.reportFile());
    }

    private static JobParameters parameters(String reportType, String startDate, String endDate) {
        return new JobParametersBuilder()
                .addString(TranReportJobConfiguration.PARAM_REPORT_TYPE, reportType)
                .addString(TranReportJobConfiguration.PARAM_START_DATE, startDate)
                .addString(TranReportJobConfiguration.PARAM_END_DATE, endDate)
                .addString("run", String.valueOf(System.nanoTime()))
                .toJobParameters();
    }

    private static List<String> lines(JobExecution execution, String key) throws IOException {
        Path file = Path.of(execution.getExecutionContext().getString(key));
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }
}
