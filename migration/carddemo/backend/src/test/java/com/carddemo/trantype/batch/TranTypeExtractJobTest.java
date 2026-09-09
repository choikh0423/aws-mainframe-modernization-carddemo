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
 * TRANEXTR ({@code jcl/TRANEXTR.jcl}) against its own in-memory database and a
 * temporary "HLQ" directory standing in for {@code AWS.M2.CARDDEMO}.
 *
 * <p>Covers FR-X01..FR-X04.
 */
@SpringBatchTest
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-trantype-extract;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class TranTypeExtractJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    @Qualifier("tranTypeExtractJob")
    private Job tranTypeExtractJob;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    private Path hlq;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(tranTypeExtractJob);
        jdbcTemplate.update("delete from db2_transaction_type_category");
        jdbcTemplate.update("delete from db2_transaction_type");
        jdbcTemplate.update("insert into db2_transaction_type (tr_type, tr_description)"
                + " values ('02', 'Payment')");
        jdbcTemplate.update("insert into db2_transaction_type (tr_type, tr_description)"
                + " values ('01', 'Purchase')");
        jdbcTemplate.update("insert into db2_transaction_type_category"
                + " (trc_type_code, trc_type_category, trc_cat_data)"
                + " values ('01', '0002', 'Regular Cash Advance')");
        jdbcTemplate.update("insert into db2_transaction_type_category"
                + " (trc_type_code, trc_type_category, trc_cat_data)"
                + " values ('01', '0001', 'Regular Sales Draft')");
    }

    @Test
    void unloadsBothTablesAsSixtyByteRecordsAfterBackingUpTheLastRun() throws Exception {
        Files.writeString(hlq.resolve(TranTypeExtractJobConfiguration.TRANTYPE_PS), "previous run\n");
        Files.writeString(hlq.resolve(TranTypeExtractJobConfiguration.TRANCATG_PS), "previous run\n");

        JobExecution execution = jobLauncherTestUtils.launchJob(parameters("extract"));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions()).extracting(StepExecution::getStepName)
                .containsExactly("STEP10", "STEP20", "STEP30", "STEP40", "STEP50");

        assertThat(Files.readString(hlq.resolve(
                TranTypeExtractJobConfiguration.TRANTYPE_BKUP + ".G0001V00")))
                .isEqualTo("previous run\n");
        assertThat(Files.readString(hlq.resolve(
                TranTypeExtractJobConfiguration.TRANCATG_BKUP + ".G0001V00")))
                .isEqualTo("previous run\n");

        List<String> types = read(TranTypeExtractJobConfiguration.TRANTYPE_PS);
        assertThat(types).containsExactly(
                "01" + fixed("Purchase") + "00000000",
                "02" + fixed("Payment") + "00000000");
        assertThat(types).allSatisfy(record ->
                assertThat(record).hasSize(TranTypeExtractJobConfiguration.RECORD_LENGTH));

        List<String> categories = read(TranTypeExtractJobConfiguration.TRANCATG_PS);
        assertThat(categories).containsExactly(
                "01" + "0001" + fixed("Regular Sales Draft") + "0000",
                "01" + "0002" + fixed("Regular Cash Advance") + "0000");
        assertThat(categories).allSatisfy(record ->
                assertThat(record).hasSize(TranTypeExtractJobConfiguration.RECORD_LENGTH));
    }

    @Test
    void aRunWithNoPreviousExtractEndsOnAJclError() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(parameters("no-previous-extract"));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getStepExecutions()).extracting(StepExecution::getStepName)
                .containsExactly("STEP10");
        assertThat(Files.exists(hlq.resolve(TranTypeExtractJobConfiguration.TRANTYPE_PS))).isFalse();
    }

    @Test
    void eachRunAddsTheNextGdgGeneration() throws Exception {
        Files.writeString(hlq.resolve(TranTypeExtractJobConfiguration.TRANTYPE_PS), "first\n");
        Files.writeString(hlq.resolve(TranTypeExtractJobConfiguration.TRANCATG_PS), "first\n");
        jobLauncherTestUtils.launchJob(parameters("generation-one"));

        jobLauncherTestUtils.launchJob(parameters("generation-two"));

        assertThat(hlq.resolve(TranTypeExtractJobConfiguration.TRANTYPE_BKUP + ".G0002V00")).exists();
        assertThat(Files.readString(hlq.resolve(
                TranTypeExtractJobConfiguration.TRANTYPE_BKUP + ".G0002V00")))
                .startsWith("01" + fixed("Purchase"));
    }

    private JobParameters parameters(String run) {
        return new JobParametersBuilder()
                .addString("hlq", hlq.toString())
                .addString("run", run)
                .toJobParameters();
    }

    private List<String> read(String dataset) throws IOException {
        return Files.readAllLines(hlq.resolve(dataset), StandardCharsets.UTF_8);
    }

    private static String fixed(String description) {
        return description + " ".repeat(50 - description.length());
    }
}
