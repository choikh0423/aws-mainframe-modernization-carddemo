package com.carddemo.batch.posttran;

import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.DailyTransactionRecord;
import com.carddemo.common.domain.TransactionRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.DailyTransactionRepository;
import com.carddemo.common.repository.TransactionCategoryBalanceRepository;
import com.carddemo.common.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * POSTTRAN end to end over the ASCII fixtures in {@code app/data/ASCII}.
 *
 * <p>The expected numbers are produced independently of the migrated code by
 * {@code migration/carddemo/tools/posttran_parity_oracle.py}, which applies
 * CBTRN02C's rules straight to the unloads: 300 read, 262 posted, 38 rejected
 * (all reason 102), 100 category balances (50 of them created), condition
 * code 4.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-posttran;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "carddemo.batch.posttran.reject-dir=target/posttran-test/dalyrejs"
})
class PostTranJobIntegrationTest {

    /** posttran_parity_oracle.py. */
    private static final int EXPECTED_READ = 300;
    private static final int EXPECTED_POSTED = 262;
    private static final int EXPECTED_REJECTED = 38;
    private static final int EXPECTED_CATEGORY_BALANCES = 100;

    /** The 151st DALYTRAN record, in the middle of the second chunk. */
    private static final String RECORD_IN_SECOND_CHUNK = "0000000498857207";

    /**
     * {@code @SpringBatchTest} is not used: its scope listeners hijack any test
     * method returning a JobExecution or a StepExecution, and POSTTRAN is not
     * the only Job bean, so the utils are wired to it explicitly.
     */
    @TestConfiguration
    static class JobUnderTest {

        @Bean
        JobLauncherTestUtils postTranJobLauncherTestUtils(JobLauncher jobLauncher, JobRepository jobRepository,
                Job postTranJob) {
            JobLauncherTestUtils utils = new JobLauncherTestUtils();
            utils.setJobLauncher(jobLauncher);
            utils.setJobRepository(jobRepository);
            utils.setJob(postTranJob);
            return utils;
        }
    }

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    private TransactionRepository transactions;
    @Autowired
    private DailyTransactionRepository dailyTransactions;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private TransactionCategoryBalanceRepository categoryBalances;
    @Autowired
    private PostTranJobListener conditionCode;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DataSource dataSource;
    @SpyBean
    private TransactionPostingService postingService;

    /** JUnit builds a fresh instance per test, so the counter has to be shared. */
    private static final AtomicInteger RUN = new AtomicInteger();

    @BeforeEach
    void reseed() throws Exception {
        reset(postingService);
        jdbcTemplate.execute("DELETE FROM transactions");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/seed/R__seed_carddemo_data.sql"));
        }
    }

    @Test
    void jobAndStepAreNamedAfterTheJcl() throws Exception {
        JobExecution execution = launch();

        assertThat(execution.getJobInstance().getJobName()).isEqualTo("POSTTRAN");
        assertThat(execution.getStepExecutions()).extracting(step -> step.getStepName())
                .containsExactly("STEP15");
    }

    @Test
    void postsEveryValidDailyTransactionOnce() throws Exception {
        JobExecution execution = launch();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(readCount(execution)).isEqualTo(EXPECTED_READ);
        assertThat(writeCount(execution)).isEqualTo(EXPECTED_REJECTED);
        assertThat(transactions.count()).isEqualTo(EXPECTED_POSTED);
        assertThat(dailyTransactions.count()).isEqualTo(EXPECTED_READ);

        // every posted transaction is its daily transaction, with a fresh processing timestamp
        List<TransactionRecord> posted = transactions.findAll();
        assertThat(posted).allSatisfy(transaction -> {
            DailyTransactionRecord daily = dailyTransactions.findById(transaction.getId()).orElseThrow();
            assertThat(transaction.getCardNum()).isEqualTo(daily.getCardNum());
            assertThat(transaction.getAmount()).isEqualByComparingTo(daily.getAmount());
            assertThat(transaction.getOrigTs()).isEqualTo(daily.getOrigTs());
            assertThat(transaction.getProcTs()).matches("\\d{4}-\\d{2}-\\d{2}-\\d{2}\\.\\d{2}\\.\\d{2}\\.\\d{2}0000");
        });
    }

    @Test
    void writesOne430CharacterRejectLinePerRejectedTransaction() throws Exception {
        JobExecution execution = launch();

        List<String> rejects = Files.readAllLines(rejectFile(execution));
        assertThat(rejects).hasSize(EXPECTED_REJECTED);
        assertThat(rejects).allSatisfy(line -> {
            assertThat(line).hasSize(430);
            assertThat(line.substring(350)).isEqualTo(
                    "0102" + "OVERLIMIT TRANSACTION".concat(" ".repeat(55)));
            String id = line.substring(0, 16);
            assertThat(dailyTransactions.findById(id)).isPresent();
            assertThat(transactions.findById(id)).isEmpty();
        });
    }

    @Test
    void returnsConditionCode4WhenAnythingWasRejected() throws Exception {
        launch();

        assertThat(conditionCode.getExitCode()).isEqualTo(4);
    }

    @Test
    void returnsConditionCode0WhenNothingWasRejected() throws Exception {
        // the 38 rejects are all overlimit, so a limit nothing can exceed clears them
        jdbcTemplate.execute("UPDATE accounts SET credit_limit = 9999999999.99");

        JobExecution execution = launch();

        assertThat(writeCount(execution)).isZero();
        assertThat(transactions.count()).isEqualTo(EXPECTED_READ);
        assertThat(conditionCode.getExitCode()).isZero();
        assertThat(rejectFile(execution)).isEmptyFile();
    }

    @Test
    void accumulatesAccountEffectsInInputOrder() throws Exception {
        launch();

        // posttran_parity_oracle.py, account 1: the 102 rejects depend on the cycle
        // totals the preceding transactions of the same run left behind
        AccountRecord account = accounts.findById(1L).orElseThrow();
        assertThat(account.getCurrBal()).isEqualByComparingTo(new BigDecimal("1288.10"));
        assertThat(account.getCurrCycCredit()).isEqualByComparingTo(new BigDecimal("1164.87"));
        assertThat(account.getCurrCycDebit()).isEqualByComparingTo(new BigDecimal("-70.77"));
    }

    @Test
    void createsTheCategoryBalancesTheSeedDoesNotHaveAndAddsToTheOnesItDoes() throws Exception {
        BigDecimal seeded = categoryBalance("SELECT bal FROM transaction_category_balances"
                + " WHERE acct_id = 1 AND type_cd = '01' AND cat_cd = 1");

        launch();

        // the seed holds 50 balances, the run posts into 50 more combinations
        assertThat(categoryBalances.count()).isEqualTo(EXPECTED_CATEGORY_BALANCES);

        // an existing balance is the seeded one plus the transactions posted into it
        BigDecimal posted = categoryBalance("SELECT coalesce(sum(t.amount), 0) FROM transactions t"
                + " JOIN card_xref x ON x.card_num = t.card_num"
                + " WHERE x.acct_id = 1 AND t.type_cd = '01' AND t.cat_cd = 1");
        assertThat(categoryBalance("SELECT bal FROM transaction_category_balances"
                + " WHERE acct_id = 1 AND type_cd = '01' AND cat_cd = 1"))
                .isEqualByComparingTo(seeded.add(posted));

        // and no balance, created or seeded, holds anything but what was posted into it
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM transaction_category_balances b"
                + " LEFT JOIN (SELECT x.acct_id acct_id, t.type_cd type_cd, t.cat_cd cat_cd, sum(t.amount) posted"
                + "   FROM transactions t JOIN card_xref x ON x.card_num = t.card_num"
                + "   GROUP BY x.acct_id, t.type_cd, t.cat_cd) p"
                + " ON p.acct_id = b.acct_id AND p.type_cd = b.type_cd AND p.cat_cd = b.cat_cd"
                + " WHERE b.bal <> coalesce(p.posted, 0)", Integer.class)).isZero();
    }

    @Test
    void leavesAccountsUntouchedForRejectedTransactions() throws Exception {
        // a positive amount over a zero credit limit rejects every record, refunds included
        jdbcTemplate.execute("UPDATE accounts SET credit_limit = 0, curr_cyc_credit = 0, curr_cyc_debit = 0");
        jdbcTemplate.execute("UPDATE daily_transactions SET amount = 100.00");
        BigDecimal balancesBefore = categoryBalance("SELECT sum(curr_bal) FROM accounts");
        BigDecimal categoriesBefore = categoryBalance("SELECT sum(bal) FROM transaction_category_balances");

        JobExecution execution = launch();

        assertThat(writeCount(execution)).isEqualTo(EXPECTED_READ);
        assertThat(transactions.count()).isZero();
        assertThat(categoryBalance("SELECT sum(curr_bal) FROM accounts"))
                .isEqualByComparingTo(balancesBefore);
        assertThat(categoryBalance("SELECT sum(bal) FROM transaction_category_balances"))
                .isEqualByComparingTo(categoriesBefore);
        assertThat(categoryBalances.count()).isEqualTo(50);
    }

    @Test
    void logsTheCobolExecutionMarkersAndCounts(CapturedOutput output) throws Exception {
        launch();

        assertThat(output).contains("START OF EXECUTION OF PROGRAM CBTRN02C");
        assertThat(output).contains("TRANSACTIONS PROCESSED :000000300");
        assertThat(output).contains("TRANSACTIONS REJECTED  :000000038");
        assertThat(output).contains("END OF EXECUTION OF PROGRAM CBTRN02C");
        assertThat(output).contains("TCATBAL record not found for key : ");
    }

    @Test
    void emptiesTheTransactionMasterBeforePosting() throws Exception {
        jdbcTemplate.update("INSERT INTO transactions (id, type_cd, cat_cd, source, description, amount,"
                + " merchant_id, merchant_name, merchant_city, merchant_zip, card_num, orig_ts, proc_ts)"
                + " VALUES ('LEFTOVERPREVRUN ', '01', 1, 'POS TERM', 'from the previous run', 1.00,"
                + " 1, 'M', 'C', 'Z', '4111111111111111', '', '')");

        launch();

        assertThat(transactions.findById("LEFTOVERPREVRUN ")).isEmpty();
        assertThat(transactions.count()).isEqualTo(EXPECTED_POSTED);
    }

    @Test
    void neverWritesReason109() throws Exception {
        JobExecution execution = launch();

        assertThat(Files.readAllLines(rejectFile(execution)))
                .noneMatch(line -> line.startsWith("0109", 350));
    }

    @Test
    void indexesPostedTransactionsByProcessingTimestamp() throws Exception {
        launch();

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM information_schema.indexes"
                + " WHERE index_name = 'IDX_TRANSACTIONS_PROC_TS'", Integer.class)).isEqualTo(1);
        String procTs = transactions.findAll().get(0).getProcTs();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM transactions WHERE proc_ts = ?", Integer.class, procTs))
                .isPositive();
    }

    @Test
    void restartsFromTheLastCommittedChunk() throws Exception {
        JobParameters parameters = new JobParametersBuilder()
                .addString("run", "posttran-restart-" + RUN.incrementAndGet()).toJobParameters();
        doThrow(new IllegalStateException("simulated DASD failure"))
                .when(postingService).post(argThatIsRecord(), any());

        JobExecution failed = jobLauncherTestUtils.launchJob(parameters);
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        long postedBeforeRestart = transactions.count();
        assertThat(postedBeforeRestart).isPositive().isLessThan(EXPECTED_POSTED);

        reset(postingService);
        JobExecution restarted = jobLauncherTestUtils.launchJob(parameters);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getStepExecutions()).allSatisfy(step ->
                assertThat(step.getReadCount()).isLessThan(EXPECTED_READ));
        assertThat(transactions.count()).isEqualTo(EXPECTED_POSTED);
        assertThat(Files.readAllLines(rejectFile(restarted))).hasSize(EXPECTED_REJECTED);
        // the restart resumed instead of re-emptying TRANSACT and reposting from the top
        assertThat(accounts.findById(1L).orElseThrow().getCurrBal())
                .isEqualByComparingTo(new BigDecimal("1288.10"));
    }

    private DailyTransactionRecord argThatIsRecord() {
        return org.mockito.ArgumentMatchers.argThat(daily ->
                daily != null && RECORD_IN_SECOND_CHUNK.equals(daily.getId()));
    }

    private JobExecution launch() throws Exception {
        return jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", "posttran-" + RUN.incrementAndGet())
                .toJobParameters());
    }

    private BigDecimal categoryBalance(String sql) {
        return jdbcTemplate.queryForObject(sql, BigDecimal.class);
    }

    private static long readCount(JobExecution execution) {
        return execution.getStepExecutions().iterator().next().getReadCount();
    }

    private static long writeCount(JobExecution execution) {
        return execution.getStepExecutions().iterator().next().getWriteCount();
    }

    private static Path rejectFile(JobExecution execution) {
        return Path.of(execution.getExecutionContext().getString(PostTranJobListener.REJECT_FILE_KEY));
    }
}
