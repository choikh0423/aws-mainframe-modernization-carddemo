package com.carddemo.batch.statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-01 to FR-09 and FR-38 to FR-42: the CREASTMT job end to end on the seeded
 * database, in its own schema so the other tests keep the untouched seed.
 */
@SpringBatchTest
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-statement;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "carddemo.batch.statement.output-dir=target/statements-test"
})
class CreateStatementJobTest {

    private static final Path OUTPUT_DIR = Path.of("target/statements-test");

    /** A card_xref row of the ASCII estate: customer 50, account 50. */
    private static final String CARD = "0500024453765740";

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    @Qualifier("createStatementJob")
    private Job createStatementJob;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StatementWorkTransactionRepository workTransactions;

    @BeforeEach
    void resetEstate() {
        jobLauncherTestUtils.setJob(createStatementJob);
        // The transaction demo seed adds five card_xref rows whose customers and
        // accounts were never unloaded; see the stream's migration plan.
        jdbcTemplate.update("DELETE FROM card_xref WHERE cust_id > 50");
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("INSERT INTO transactions (id, type_cd, cat_cd, source, description,"
                + " amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_num,"
                + " orig_ts, proc_ts) VALUES"
                + " ('0000000000000042', '01', 1000, 'POS', 'GROCERY PURCHASE', 13.75, 900000001,"
                + " 'ACME SUPERMARKET', 'NEW YORK', '10001', '" + CARD + "',"
                + " '2023-06-01-10.15.31.000000', '2023-06-01-23.59.51.000000'),"
                + " ('0000000000000043', '02', 2000, 'ONLINE', 'FUEL PURCHASE', -17.50, 900000002,"
                + " 'GLOBEX FUEL', 'CHICAGO', '60601', '" + CARD + "',"
                + " '2023-06-02-10.15.32.000000', '2023-06-02-23.59.52.000000')");
    }

    @Test
    void runsTheFiveCreastmtStepsAndWritesBothReports() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(uniqueParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions()).extracting(StepExecution::getStepName)
                .containsExactly("DELDEF01", "STEP010", "STEP020", "STEP030", "STEP040");
        assertThat(workTransactions.count()).isEqualTo(2);

        List<String> text = read(CreateStatementJobConfiguration.TEXT_FILE);
        assertThat(text).allSatisfy(line ->
                assertThat(line).hasSize(StatementRenderer.TEXT_RECORD_LENGTH));
        assertThat(text.stream().filter(line -> line.contains("START OF STATEMENT")).count())
                .isEqualTo(50);
        assertThat(text.stream().filter(line -> line.contains("END OF STATEMENT")).count())
                .isEqualTo(50);

        List<String> html = read(CreateStatementJobConfiguration.HTML_FILE);
        assertThat(html).allSatisfy(line ->
                assertThat(line).hasSize(StatementRenderer.HTML_RECORD_LENGTH));
        assertThat(html.get(0).stripTrailing()).isEqualTo("<!DOCTYPE html>");
        assertThat(html.stream().filter(line -> line.startsWith("</html>")).count()).isEqualTo(50);
    }

    @Test
    void printsTheTransactionsAndTotalOfTheCardTheyBelongTo() throws Exception {
        jobLauncherTestUtils.launchJob(uniqueParameters());

        List<String> text = read(CreateStatementJobConfiguration.TEXT_FILE);
        List<String> detail = text.stream()
                .filter(line -> line.startsWith("00000000000000"))
                .map(String::stripTrailing)
                .toList();

        assertThat(detail).hasSize(2);
        assertThat(detail.get(0)).endsWith("$       13.75");
        assertThat(detail.get(1)).endsWith("$       17.50-");
        assertThat(text.stream().map(String::stripTrailing).filter(line -> line.startsWith("Total EXP:")))
                .contains("Total EXP:" + " ".repeat(56) + "$        3.75-");
    }

    @Test
    void abendsWhenAnXrefPointsAtAMissingCustomer() throws Exception {
        jdbcTemplate.update("INSERT INTO card_xref (card_num, cust_id, acct_id)"
                + " VALUES ('4111111111111111', 111000001, 10000000001)");

        JobExecution execution = jobLauncherTestUtils.launchJob(uniqueParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        // STEP030 deleted the previous run's reports before STEP040 abended.
        assertThat(read(CreateStatementJobConfiguration.TEXT_FILE)).isEmpty();
        assertThat(read(CreateStatementJobConfiguration.HTML_FILE)).isEmpty();
        assertThat(execution.getAllFailureExceptions())
                .anySatisfy(failure -> assertThat(failure.getMessage())
                        .contains("ABEND-CULPRIT=CBSTM03A")
                        .contains("ABEND-MSG=ERROR READING CUSTFILE"));
    }

    @Test
    void rebuildsTheWorkStoreFromScratchOnEveryRun() throws Exception {
        jobLauncherTestUtils.launchJob(uniqueParameters());
        JobExecution second = jobLauncherTestUtils.launchJob(uniqueParameters());

        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(workTransactions.count()).isEqualTo(2);
        assertThat(workTransactions.findAll()).extracting(work -> work.getCardNum() + work.getTranId())
                .containsExactlyInAnyOrder(CARD + "0000000000000042", CARD + "0000000000000043");
    }

    private static JobParameters uniqueParameters() {
        return new JobParametersBuilder()
                .addString("run", UUID.randomUUID().toString())
                .toJobParameters();
    }

    private static List<String> read(String fileName) throws IOException {
        return Files.readAllLines(OUTPUT_DIR.resolve(fileName));
    }
}
