package com.carddemo.trantype.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CREADB21 ({@code jcl/CREADB21.jcl}) against its own in-memory database.
 *
 * <p>Covers FR-C01..FR-C05, including the source's own {@code REVERAL} spelling
 * and the duplicate-key failure of a second run.
 */
@SpringBatchTest
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-trantype-create;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class CreateDb2TablesJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    @Qualifier("createDb2TablesJob")
    private Job createDb2TablesJob;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(createDb2TablesJob);
        jdbcTemplate.update("delete from db2_transaction_type_category");
        jdbcTemplate.update("delete from db2_transaction_type");
    }

    @Test
    void loadsTheControlMembersThroughOneStepPerExecPgm() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", "create")
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions()).extracting(StepExecution::getStepName)
                .containsExactly("FREEPLN", "CRCRDDB", "LDTTYPE", "RUNTEP2", "LDTCCAT");

        assertThat(count("db2_transaction_type")).isEqualTo(7);
        assertThat(count("db2_transaction_type_category")).isEqualTo(18);
        assertThat(jdbcTemplate.queryForObject(
                "select tr_description from db2_transaction_type where tr_type = '06'", String.class))
                .isEqualTo("REVERAL");
        assertThat(jdbcTemplate.queryForObject(
                "select trc_cat_data from db2_transaction_type_category"
                        + " where trc_type_code = '07' and trc_type_category = '0001'", String.class))
                .isEqualTo("SALES DRAFT CREDIT ADJUSTMENT");
    }

    @Test
    void aSecondRunAgainstPopulatedTablesFailsOnTheUniqueIndex() throws Exception {
        jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", "first-run")
                .toJobParameters());

        JobExecution second = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", "second-run")
                .toJobParameters());

        assertThat(second.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(second.getStepExecutions()).extracting(StepExecution::getStepName)
                .containsExactly("FREEPLN", "CRCRDDB", "LDTTYPE", "RUNTEP2");
        assertThat(count("db2_transaction_type")).isEqualTo(7);
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
