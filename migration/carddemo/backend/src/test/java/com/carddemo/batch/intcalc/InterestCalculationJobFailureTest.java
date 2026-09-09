package com.carddemo.batch.intcalc;

import com.carddemo.common.batch.AbendException;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.CardXrefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * INTCALC's failure paths (FR-I2, FR-I18, FR-I20, FR-I21), on their own database
 * because they leave the estate deliberately broken.
 */
@SpringBatchTest
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-intcalc-fail;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class InterestCalculationJobFailureTest {

    @TempDir
    Path workDir;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    @Qualifier("interestCalculationJob")
    private Job interestCalculationJob;
    @Autowired
    private CardXrefRepository cardXrefs;
    @Autowired
    private AccountRepository accounts;

    @BeforeEach
    void useInterestCalculationJob() {
        jobLauncherTestUtils.setJob(interestCalculationJob);
    }

    /** FR-I2: the PARM is mandatory and is exactly ten characters. */
    @Test
    void rejectsARunDateThatIsNotTenCharacters() {
        assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run.date", "20220718")
                .toJobParameters()))
                .isInstanceOf(JobParametersInvalidException.class);

        assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", "no-parm")
                .toJobParameters()))
                .isInstanceOf(JobParametersInvalidException.class);
    }

    /**
     * FR-I18, FR-I20, FR-I21: a missing XREF row abends the step with the COBOL
     * messages, and nothing of the failing chunk is posted.
     */
    @Test
    void abendsAndPostsNothingWhenACardXrefIsMissing(CapturedOutput output) throws Exception {
        cardXrefs.delete(cardXrefs.findByAcctId(1L).orElseThrow());
        com.carddemo.common.domain.AccountRecord untouched = accounts.findById(2L).orElseThrow();
        untouched.setCurrCycCredit(new java.math.BigDecimal("25.00"));
        accounts.save(untouched);

        JobExecution execution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run.date", "2022071800")
                .addString("systran.file", workDir.resolve("systran.txt").toString())
                .addString("run", "intcalc-abend")
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getAllFailureExceptions()).hasAtLeastOneElementOfType(AbendException.class);
        assertThat(output).contains("START OF EXECUTION OF PROGRAM CBACT04C");
        assertThat(output).contains("ACCOUNT NOT FOUND: 00000000001");
        assertThat(output).contains("ERROR READING XREF FILE");
        assertThat(accounts.findById(2L).orElseThrow().getCurrCycCredit())
                .isEqualByComparingTo("25.00");
        assertThat(execution.getStepExecutions().iterator().next().getWriteCount()).isZero();
    }
}
