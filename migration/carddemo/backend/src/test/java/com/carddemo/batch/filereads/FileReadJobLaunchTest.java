package com.carddemo.batch.filereads;

import com.carddemo.batch.BatchJobLauncher;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-G2: the four jobs run only when named on the command line, and the process exit
 * code follows the JCL condition-code convention — 0 completed, 12 failed.
 */
@SpringBootTest(properties = "carddemo.batch.output-dir=target/batch-output/launch-test")
class FileReadJobLaunchTest {

    private static final Path OUTPUT_DIR = Path.of("target/batch-output/launch-test");

    @Autowired
    private JobLauncher jobLauncher;
    @Autowired
    private List<Job> jobs;

    @Test
    void runsNothingWhenNoJobIsNamed() throws Exception {
        Files.createDirectories(OUTPUT_DIR);
        Path sysout = OUTPUT_DIR.resolve("READXREF.SYSOUT.txt");
        Files.deleteIfExists(sysout);
        BatchJobLauncher launcher = new BatchJobLauncher(jobLauncher, jobs, "");

        launcher.run(new DefaultApplicationArguments());

        assertThat(launcher.getExitCode()).isZero();
        assertThat(Files.exists(sysout)).isFalse();
    }

    @Test
    void runsTheNamedJobAndExitsZero() throws Exception {
        BatchJobLauncher launcher = new BatchJobLauncher(jobLauncher, jobs, "READCUST");

        launcher.run(new DefaultApplicationArguments("--run=launch-" + System.nanoTime()));

        assertThat(launcher.getExitCode()).isZero();
        assertThat(FileReadJobTestSupport.sysout(OUTPUT_DIR, "READCUST"))
                .first().isEqualTo("START OF EXECUTION OF PROGRAM CBCUS01C");
    }

    @Test
    void exitsTwelveWhenTheJobFails() throws Exception {
        Job failing = new Job() {
            @Override
            public String getName() {
                return "READFAIL";
            }

            @Override
            public void execute(JobExecution execution) {
                execution.setStatus(BatchStatus.FAILED);
                execution.setExitStatus(org.springframework.batch.core.ExitStatus.FAILED);
            }
        };
        BatchJobLauncher launcher = new BatchJobLauncher(jobLauncher, List.of(failing), "READFAIL");

        launcher.run(new DefaultApplicationArguments("--run=fail-" + System.nanoTime()));

        assertThat(launcher.getExitCode()).isEqualTo(BatchJobLauncher.EXIT_CODE_FAILED);
    }

    @Test
    void refusesAnUnknownJobNameAndNamesTheFourStreamJobs() {
        BatchJobLauncher launcher = new BatchJobLauncher(jobLauncher, jobs, "READNOPE");

        assertThatThrownBy(() -> launcher.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("READACCT")
                .hasMessageContaining("READCARD")
                .hasMessageContaining("READXREF")
                .hasMessageContaining("READCUST");
    }

    @Test
    void passesRemainingArgumentsThroughAsJobParameters() throws Exception {
        BatchJobLauncher launcher = new BatchJobLauncher(jobLauncher, jobs, "READCUST");

        launcher.run(new DefaultApplicationArguments(
                "--spring.profiles.active=h2", "--run=params-" + System.nanoTime()));

        assertThat(launcher.getExitCode()).isZero();
    }
}
