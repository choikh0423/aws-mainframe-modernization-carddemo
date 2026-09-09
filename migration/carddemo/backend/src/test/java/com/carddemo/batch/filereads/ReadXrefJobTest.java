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
 * FR-X1 to FR-X3: READXREF prints the cross-reference file in card-number order,
 * twice per record — CBACT03C displays in both the read paragraph and the main loop.
 */
@SpringBatchTest
@SpringBootTest(properties = "carddemo.batch.output-dir=target/batch-output/readxref-test")
class ReadXrefJobTest {

    private static final Path OUTPUT_DIR = Path.of("target/batch-output/readxref-test");

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    private Job readXrefJob;

    @BeforeEach
    void selectJob() {
        jobLauncherTestUtils.setJob(readXrefJob);
    }

    @Test
    void printsEveryCrossReferenceTwiceInKeyOrder() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", "readxref-" + System.nanoTime())
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<String> sysout = FileReadJobTestSupport.sysout(OUTPUT_DIR, "READXREF");
        assertThat(sysout.get(0)).isEqualTo("START OF EXECUTION OF PROGRAM CBACT03C");
        assertThat(sysout.get(sysout.size() - 1)).isEqualTo("END OF EXECUTION OF PROGRAM CBACT03C");

        List<String> images = sysout.subList(1, sysout.size() - 1);
        assertThat(images).allMatch(line -> line.length() == RecordImages.XREF_LENGTH);
        assertThat(images).isSorted();

        // Every record of the unload is printed twice; the seed also carries the five
        // CT02 cross-references of the transaction stream, which are printed the same way.
        for (String record : FileReadJobTestSupport.fixture("cardxref.txt", RecordImages.XREF_LENGTH)) {
            assertThat(images).containsSequence(record, record);
        }
    }
}
