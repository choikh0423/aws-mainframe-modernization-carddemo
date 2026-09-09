package com.carddemo.batch.filereads;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-C1 to FR-C3: READCARD prints the card file in card-number order, once per card,
 * between the two execution messages of CBACT02C.
 */
@SpringBatchTest
@SpringBootTest(properties = "carddemo.batch.output-dir=target/batch-output/readcard-test")
class ReadCardJobTest {

    private static final Path OUTPUT_DIR = Path.of("target/batch-output/readcard-test");

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    private Job readCardJob;

    @BeforeEach
    void selectJob() {
        jobLauncherTestUtils.setJob(readCardJob);
    }

    @Test
    void printsEveryCardOnceInKeyOrder() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", "readcard-" + System.nanoTime())
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions()).extracting(step -> step.getStepName())
                .containsExactly("STEP05");

        List<String> sysout = FileReadJobTestSupport.sysout(OUTPUT_DIR, "READCARD");
        assertThat(sysout.get(0)).isEqualTo("START OF EXECUTION OF PROGRAM CBACT02C");
        assertThat(sysout.get(sysout.size() - 1)).isEqualTo("END OF EXECUTION OF PROGRAM CBACT02C");

        List<String> images = sysout.subList(1, sysout.size() - 1);
        List<String> expected = FileReadJobTestSupport.fixture("carddata.txt",
                RecordImages.CARD_LENGTH).stream().sorted().toList();
        assertThat(images).isEqualTo(expected);
        assertThat(images).allMatch(line -> line.length() == RecordImages.CARD_LENGTH);
    }
}
