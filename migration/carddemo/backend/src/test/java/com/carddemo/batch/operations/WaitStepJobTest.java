package com.carddemo.batch.operations;

import com.carddemo.common.batch.AbendException;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the WAITSTEP job the way the launcher runs it.
 *
 * <p>Covers FR-OC-01, FR-OC-04, FR-OC-07, FR-OC-10, FR-OC-12.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-waitstep;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class WaitStepJobTest {

    @Autowired
    private JobLauncher jobLauncher;
    @Autowired
    @Qualifier("waitStepJob")
    private Job waitStepJob;

    @Test
    void jobAndStepAreNamedAfterTheJclTheyReplace() {
        assertThat(waitStepJob.getName()).isEqualTo("WAITSTEP");
    }

    @Test
    void waitsForTheRequestedNumberOfCentisecondsAndCompletes() throws Exception {
        long before = System.nanoTime();

        JobExecution execution = jobLauncher.run(waitStepJob, new JobParametersBuilder()
                .addString(WaitStepJobConfiguration.SYSIN_PARAMETER, "00000020")
                .addString("run", "wait-20")
                .toJobParameters());

        long elapsedMillis = (System.nanoTime() - before) / 1_000_000L;
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        assertThat(execution.getStepExecutions())
                .extracting(StepExecution::getStepName)
                .containsExactly("WAIT");
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(200L);
        assertThat(execution.getStepExecutions())
                .allSatisfy(step -> {
                    assertThat(step.getReadCount()).isZero();
                    assertThat(step.getWriteCount()).isZero();
                });
    }

    @Test
    void defaultsToTheControlCardShippedWithTheJcl() throws Exception {
        WaitControlCard packaged = WaitControlCard.parse(new String(getClass().getClassLoader()
                .getResourceAsStream(WaitStepJobConfiguration.SYSIN_RESOURCE).readAllBytes()));

        assertThat(packaged.value()).isEqualTo("00003600");
        assertThat(packaged.centiseconds()).isEqualTo(3600L);
    }

    @Test
    void failsTheJobWhenTheControlCardIsNotNumeric() throws Exception {
        JobExecution execution = jobLauncher.run(waitStepJob, new JobParametersBuilder()
                .addString(WaitStepJobConfiguration.SYSIN_PARAMETER, "ABCDEFGH")
                .addString("run", "wait-bad-card")
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getAllFailureExceptions())
                .hasAtLeastOneElementOfType(AbendException.class);
    }
}
