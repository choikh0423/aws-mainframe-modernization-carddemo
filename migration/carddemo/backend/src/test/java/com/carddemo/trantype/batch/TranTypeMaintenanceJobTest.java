package com.carddemo.trantype.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MNTTRDB2 ({@code COBTUPDT.cbl}) against its own in-memory database.
 *
 * <p>Each INPFILE record is the fixed 53 bytes the FD describes: the operation
 * in column 1, the two-digit type in 2-3 and the 50-byte description in 4-53.
 *
 * <p>Covers FR-B01..FR-B10.
 */
@SpringBatchTest
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-trantype-batch;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class TranTypeMaintenanceJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    @Qualifier("tranTypeMaintenanceJob")
    private Job tranTypeMaintenanceJob;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    private Path workDir;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(tranTypeMaintenanceJob);
        jdbcTemplate.update("delete from db2_transaction_type_category");
        jdbcTemplate.update("delete from db2_transaction_type");
        jdbcTemplate.update("insert into db2_transaction_type (tr_type, tr_description)"
                + " values ('01', 'Purchase')");
        jdbcTemplate.update("insert into db2_transaction_type (tr_type, tr_description)"
                + " values ('02', 'Payment')");
        jdbcTemplate.update("insert into db2_transaction_type_category"
                + " (trc_type_code, trc_type_category, trc_cat_data) values ('02', '0001', 'Child')");
    }

    @Test
    void appliesAddUpdateAndDeleteAndIgnoresCommentedLines() throws Exception {
        Path input = inpfile(
                record("*", "99", "this line is a comment"),
                record("A", "42", "LATE FEE"),
                record("U", "01", "PURCHASE AMENDED"),
                record("D", "01", ""));

        JobExecution execution = jobLauncherTestUtils.launchJob(parameters(input, "apply"));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions()).extracting(StepExecution::getStepName)
                .containsExactly("STEP1");
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        assertThat(description("42")).isEqualTo("LATE FEE");
        assertThat(count("01")).isZero();
    }

    @Test
    void anUnknownOperationEndsTheStepWithReturnCode4ButKeepsProcessing() throws Exception {
        Path input = inpfile(
                record("X", "01", "PURCHASE"),
                record("A", "42", "LATE FEE"));

        JobExecution execution = jobLauncherTestUtils.launchJob(parameters(input, "bad-operation"));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getExitStatus().getExitCode())
                .isEqualTo(TranTypeMaintenanceJobConfiguration.RC_4.getExitCode());
        assertThat(description("42")).isEqualTo("LATE FEE");
    }

    @Test
    void aDuplicateInsertAMissingUpdateAndAConstrainedDeleteAllRaiseReturnCode4() throws Exception {
        Path input = inpfile(
                record("A", "01", "PURCHASE"),
                record("U", "77", "NOT THERE"),
                record("D", "02", ""));

        JobExecution execution = jobLauncherTestUtils.launchJob(parameters(input, "sql-errors"));

        assertThat(execution.getExitStatus().getExitCode())
                .isEqualTo(TranTypeMaintenanceJobConfiguration.RC_4.getExitCode());
        assertThat(description("01")).isEqualTo("Purchase");
        assertThat(count("02")).isEqualTo(1);
    }

    @Test
    void aShortRecordIsPaddedToTheFullFixedLength() throws Exception {
        Path input = inpfile("A03CREDIT");

        JobExecution execution = jobLauncherTestUtils.launchJob(parameters(input, "short-record"));

        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        assertThat(description("03")).isEqualTo("CREDIT");
    }

    private static JobParameters parameters(Path input, String run) {
        return new JobParametersBuilder()
                .addString("inpfile", input.toString())
                .addString("run", run)
                .toJobParameters();
    }

    private Path inpfile(String... records) throws IOException {
        Path input = workDir.resolve("INPFILE.txt");
        Files.write(input, List.of(records), StandardCharsets.UTF_8);
        return input;
    }

    private static String record(String operation, String typeCode, String description) {
        return operation + typeCode
                + description + " ".repeat(50 - description.length());
    }

    private String description(String typeCode) {
        return jdbcTemplate.queryForObject(
                "select tr_description from db2_transaction_type where tr_type = ?",
                String.class, typeCode);
    }

    private int count(String typeCode) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from db2_transaction_type where tr_type = ?", Integer.class, typeCode);
        return count == null ? 0 : count;
    }
}
