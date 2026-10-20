package com.carddemo.batch.dailytrn;

import com.carddemo.batch.posttran.PostTranJobListener;
import com.carddemo.batch.posttran.TransactionBackupGenerations;
import com.carddemo.batch.posttran.TransactionBackupJobConfiguration;
import com.carddemo.batch.posttran.TransactionMasterImage;
import com.carddemo.batch.posttran.TransactionPostingService;
import com.carddemo.common.domain.DailyTransactionRecord;
import com.carddemo.common.domain.TransactionRecord;
import com.carddemo.common.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.util.FileSystemUtils;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * DAILYTRN end to end over the ASCII fixtures: TRANBKP, CBPAUP0J, POSTTRAN and
 * WAITSTEP launched in order as one chain, with the POSTTRAN parity numbers of
 * {@code posttran_parity_oracle.py} (300 read, 262 posted, 38 rejected).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-dailytrn;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "carddemo.batch.posttran.reject-dir=target/dailytrn-test/dalyrejs",
        "carddemo.batch.posttran.backup-dir=target/dailytrn-test/transact-bkup"
})
class DailyTransactionChainJobIntegrationTest {

    private static final Path RUN_DIR = Path.of("target/dailytrn-test");
    private static final Path BACKUP_DIR = RUN_DIR.resolve("transact-bkup");

    private static final int EXPECTED_READ = 300;
    private static final int EXPECTED_POSTED = 262;
    private static final int EXPECTED_REJECTED = 38;

    /** The 151st DALYTRAN record, in the middle of POSTTRAN's second chunk. */
    private static final String RECORD_IN_SECOND_CHUNK = "0000000498857207";

    private static final AtomicInteger DAY = new AtomicInteger();

    @Autowired
    private JobLauncher jobLauncher;
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private JobExplorer jobExplorer;
    @Autowired
    @Qualifier("dailyTransactionChainJob")
    private Job dailyTrn;
    @Autowired
    @Qualifier("transactionBackupJob")
    private Job tranBkp;
    @Autowired
    private TransactionRepository transactions;
    @Autowired
    private PostTranJobListener postTranConditionCode;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DataSource dataSource;
    @SpyBean
    private TransactionPostingService postingService;
    @SpyBean
    private TransactionBackupGenerations backupGenerations;

    @BeforeEach
    void reseed() throws Exception {
        reset(postingService, backupGenerations);
        FileSystemUtils.deleteRecursively(RUN_DIR);
        jdbcTemplate.execute("DELETE FROM transactions");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/seed/R__seed_carddemo_data.sql"));
        }
    }

    @Test
    void launchesTheFourJobsInChainOrder() throws Exception {
        JobParameters day = businessDay();

        JobExecution chain = jobLauncher.run(dailyTrn, day);

        assertThat(chain.getJobInstance().getJobName()).isEqualTo("DAILYTRN");
        assertThat(chain.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(chain.getStepExecutions()).extracting(StepExecution::getStepName)
                .containsExactly("TRANBKP", "CBPAUP0J", "POSTTRAN", "WAITSTEP");
        for (String child : List.of("TRANBKP", "CBPAUP0J", "POSTTRAN", "WAITSTEP")) {
            assertThat(lastExecution(child, day).getStatus()).as(child).isEqualTo(BatchStatus.COMPLETED);
        }
        // a child's run starts after the previous child's run has ended
        assertThat(lastExecution("TRANBKP", day).getEndTime())
                .isBeforeOrEqualTo(lastExecution("CBPAUP0J", day).getStartTime());
        assertThat(lastExecution("CBPAUP0J", day).getEndTime())
                .isBeforeOrEqualTo(lastExecution("POSTTRAN", day).getStartTime());
        assertThat(lastExecution("POSTTRAN", day).getEndTime())
                .isBeforeOrEqualTo(lastExecution("WAITSTEP", day).getStartTime());
    }

    @Test
    void postsTheDailyFileWithThePostTranParityNumbersAndConditionCode4() throws Exception {
        JobParameters day = businessDay();

        jobLauncher.run(dailyTrn, day);

        StepExecution step15 = lastExecution("POSTTRAN", day).getStepExecutions().iterator().next();
        assertThat(step15.getReadCount()).isEqualTo(EXPECTED_READ);
        assertThat(step15.getWriteCount()).isEqualTo(EXPECTED_REJECTED);
        assertThat(transactions.count()).isEqualTo(EXPECTED_POSTED);
        assertThat(postTranConditionCode.getExitCode()).isEqualTo(PostTranJobListener.CONDITION_CODE_REJECTS);
    }

    @Test
    void backsUpThePreviousDaysMasterBeforePostingReplacesIt() throws Exception {
        jobLauncher.run(dailyTrn, businessDay());
        List<String> firstDayPostings = transactions.findAll().stream()
                .map(TransactionRecord::getId).sorted().toList();

        JobExecution secondDay = jobLauncher.run(dailyTrn, businessDay());

        assertThat(secondDay.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(generations()).containsExactly("TRANSACT.BKUP.G0001V00", "TRANSACT.BKUP.G0002V00");
        assertThat(Files.readAllLines(BACKUP_DIR.resolve("TRANSACT.BKUP.G0001V00"))).isEmpty();
        List<String> backup = Files.readAllLines(BACKUP_DIR.resolve("TRANSACT.BKUP.G0002V00"));
        assertThat(backup).hasSize(EXPECTED_POSTED)
                .allSatisfy(record -> assertThat(record).hasSize(TransactionMasterImage.RECORD_LENGTH));
        assertThat(backup).extracting(record -> record.substring(0, 16)).containsExactlyElementsOf(firstDayPostings);
        // the master now holds the second day's postings only (fewer: day one used up credit)
        StepExecution secondDayStep15 = lastExecution("POSTTRAN", secondDay.getJobParameters())
                .getStepExecutions().iterator().next();
        assertThat(transactions.count())
                .isEqualTo(secondDayStep15.getReadCount() - secondDayStep15.getWriteCount());
    }

    @Test
    void stopsAtTheFailedJobAndARerunOfTheSameDayResumesFromIt() throws Exception {
        JobParameters day = businessDay();
        doThrow(new IllegalStateException("simulated DASD failure"))
                .when(postingService).post(recordInSecondChunk(), any());

        JobExecution failed = jobLauncher.run(dailyTrn, day);

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(failed.getStepExecutions()).extracting(StepExecution::getStepName, StepExecution::getStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("TRANBKP", BatchStatus.COMPLETED),
                        org.assertj.core.groups.Tuple.tuple("CBPAUP0J", BatchStatus.COMPLETED),
                        org.assertj.core.groups.Tuple.tuple("POSTTRAN", BatchStatus.FAILED));
        assertThat(jobRepository.getJobInstance("WAITSTEP", day)).isNull();
        long postedBeforeRestart = transactions.count();
        assertThat(postedBeforeRestart).isPositive().isLessThan(EXPECTED_POSTED);

        reset(postingService);
        JobExecution resumed = jobLauncher.run(dailyTrn, day);

        assertThat(resumed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(resumed.getJobInstance().getInstanceId()).isEqualTo(failed.getJobInstance().getInstanceId());
        assertThat(resumed.getStepExecutions()).extracting(StepExecution::getStepName)
                .containsExactly("POSTTRAN", "WAITSTEP");
        // TRANBKP did not run again: no second backup, and the partial postings were not wiped
        assertThat(generations()).containsExactly("TRANSACT.BKUP.G0001V00");
        assertThat(jobExplorer.getJobExecutions(jobRepository.getJobInstance("POSTTRAN", day))).hasSize(2);
        StepExecution restartedStep15 = lastExecution("POSTTRAN", day).getStepExecutions().iterator().next();
        assertThat(restartedStep15.getReadCount()).isLessThan(EXPECTED_READ);
        assertThat(transactions.count()).isEqualTo(EXPECTED_POSTED);
    }

    @Test
    void refusesToRunABusinessDayThatAlreadyCompleted() throws Exception {
        JobParameters day = businessDay();
        jobLauncher.run(dailyTrn, day);

        assertThatThrownBy(() -> jobLauncher.run(dailyTrn, day))
                .isInstanceOf(JobInstanceAlreadyCompleteException.class);
        assertThat(generations()).hasSize(1);
    }

    @Test
    void needsAValidBusinessDate() {
        assertThatThrownBy(() -> jobLauncher.run(dailyTrn, new JobParametersBuilder()
                .addString("sysin", "00000001").toJobParameters()))
                .isInstanceOf(JobParametersInvalidException.class)
                .hasMessageContaining("runDate");
        assertThatThrownBy(() -> jobLauncher.run(dailyTrn, new JobParametersBuilder()
                .addString("runDate", "15/06/2022").toJobParameters()))
                .isInstanceOf(JobParametersInvalidException.class)
                .hasMessageContaining("15/06/2022");
    }

    @Test
    void tranBkpLeavesTheMasterAloneWhenTheBackupCannotBeWritten() throws Exception {
        jobLauncher.run(dailyTrn, businessDay());
        Path blocker = Files.createFile(BACKUP_DIR.resolve("not-a-directory"));
        Path notAFile = blocker.resolve("TRANSACT.BKUP.G0099V00");
        doReturn(notAFile).when(backupGenerations).allocateNextGeneration();

        JobExecution tranbkp = jobLauncher.run(tranBkp, businessDay());

        assertThat(tranbkp.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(tranbkp.getExecutionContext().getString(TransactionBackupJobConfiguration.BACKUP_FILE_KEY))
                .isEqualTo(notAFile.toString());
        assertThat(tranbkp.getStepExecutions()).extracting(StepExecution::getStepName).containsExactly("STEP05R");
        assertThat(transactions.count()).isEqualTo(EXPECTED_POSTED);
    }

    private JobExecution lastExecution(String jobName, JobParameters parameters) {
        JobInstance instance = jobRepository.getJobInstance(jobName, parameters);
        assertThat(instance).as(jobName + " instance").isNotNull();
        return jobRepository.getLastJobExecution(jobName, parameters);
    }

    private static JobParameters businessDay() {
        return new JobParametersBuilder()
                .addString("runDate", LocalDate.of(2022, 6, 1).plusDays(DAY.incrementAndGet()).toString())
                .addString("expiryDays", "5")
                .addString("chkpFreq", "5")
                .addString("sysin", "00000001")
                .toJobParameters();
    }

    private static List<String> generations() throws Exception {
        try (Stream<Path> files = Files.list(BACKUP_DIR)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private static DailyTransactionRecord recordInSecondChunk() {
        return org.mockito.ArgumentMatchers.argThat(daily ->
                daily != null && RECORD_IN_SECOND_CHUNK.equals(daily.getId()));
    }
}
