package com.carddemo.batch.intcalc;

import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.TransactionCategoryBalanceId;
import com.carddemo.common.domain.TransactionCategoryBalanceRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.TransactionCategoryBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INTCALC end to end on the ASCII fixtures (app/data/ASCII), against its own
 * in-memory database so the interest posting does not disturb the seeded database
 * the other tests share.
 *
 * <p>The fixtures carry a blank ACCT-GROUP-ID and an all-zero TCATBALF, so every
 * account takes the DEFAULT disclosure group and no fixture row exercises the
 * arithmetic; the two accounts prepared here give the run something to compute
 * (see the parity section of the migration plan).
 */
@SpringBatchTest
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-intcalc;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class InterestCalculationJobIntegrationTest {

    private static final long FIRST_ACCT = 1L;
    private static final long SECOND_ACCT = 2L;
    private static final long LAST_ACCT = 50L;

    @TempDir
    Path workDir;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    @Qualifier("interestCalculationJob")
    private Job interestCalculationJob;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private TransactionCategoryBalanceRepository categoryBalances;

    @BeforeEach
    void useInterestCalculationJob() {
        jobLauncherTestUtils.setJob(interestCalculationJob);
    }

    /** FR-I1, FR-I3, FR-I9, FR-I11, FR-I15: one step, key order, truncated posting. */
    @Test
    void runsAsIntcalcStep15AndPostsTruncatedInterest() throws Exception {
        // DEFAULT/01/0001 is 15.00%: 999.99 * 15 / 1200 = 12.4998... -> 12.49, not 12.50
        setCategoryBalance(FIRST_ACCT, "999.99");
        setCategoryBalance(SECOND_ACCT, "1000.00");
        BigDecimal firstBalanceBefore = accounts.findById(FIRST_ACCT).orElseThrow().getCurrBal();
        BigDecimal secondBalanceBefore = accounts.findById(SECOND_ACCT).orElseThrow().getCurrBal();

        JobExecution execution = jobLauncherTestUtils.launchJob(parameters("intcalc-main"));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions()).extracting(StepExecution::getStepName)
                .containsExactly("STEP15");
        assertThat(accounts.findById(FIRST_ACCT).orElseThrow().getCurrBal())
                .isEqualByComparingTo(firstBalanceBefore.add(new BigDecimal("12.49")));
        assertThat(accounts.findById(SECOND_ACCT).orElseThrow().getCurrBal())
                .isEqualByComparingTo(secondBalanceBefore.add(new BigDecimal("12.50")));
    }

    /** FR-I13, FR-I19: one SYSTRAN record per fixture account, densely numbered. */
    @Test
    void writesTheSystranGeneration() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(parameters("intcalc-systran"));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        List<String> systran = Files.readAllLines(systranFile("intcalc-systran"));
        assertThat(systran).hasSize(50);
        assertThat(systran).allSatisfy(line -> assertThat(line).hasSize(350));
        assertThat(TransactionRecordLine.tranIdOf(systran.get(0))).isEqualTo("2022071800000001");
        assertThat(TransactionRecordLine.tranIdOf(systran.get(49))).isEqualTo("2022071800000050");
        assertThat(TransactionRecordLine.parse(systran.get(0)).getDescription())
                .isEqualTo("Int. for a/c 00000000001");
    }

    /** FR-I16: the last account of the run is never updated, but is still charged. */
    @Test
    void doesNotUpdateTheLastAccountOfTheRun() throws Exception {
        setCategoryBalance(LAST_ACCT, "1000.00");
        AccountRecord before = accounts.findById(LAST_ACCT).orElseThrow();
        BigDecimal balanceBefore = before.getCurrBal();

        jobLauncherTestUtils.launchJob(parameters("intcalc-last-account"));

        assertThat(accounts.findById(LAST_ACCT).orElseThrow().getCurrBal())
                .isEqualByComparingTo(balanceBefore);
        List<String> systran = Files.readAllLines(systranFile("intcalc-last-account"));
        assertThat(TransactionRecordLine.parse(systran.get(49)).getDescription())
                .isEqualTo("Int. for a/c 00000000050");
    }

    /** FR-I20: the program banners and the read trace. */
    @Test
    void logsTheProgramBanners(CapturedOutput output) throws Exception {
        jobLauncherTestUtils.launchJob(parameters("intcalc-banners"));

        assertThat(output).contains("START OF EXECUTION OF PROGRAM CBACT04C");
        assertThat(output).contains("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    private void setCategoryBalance(long acctId, String balance) {
        TransactionCategoryBalanceRecord record = categoryBalances
                .findById(new TransactionCategoryBalanceId(acctId, "01", 1)).orElseThrow();
        record.setBal(new BigDecimal(balance));
        categoryBalances.save(record);
    }

    private org.springframework.batch.core.JobParameters parameters(String run) {
        return new JobParametersBuilder()
                .addString("run.date", "2022071800")
                .addString("systran.file", systranFile(run).toString())
                .addString("run", run)
                .toJobParameters();
    }

    private Path systranFile(String run) {
        return workDir.resolve(run + ".systran.txt");
    }
}
