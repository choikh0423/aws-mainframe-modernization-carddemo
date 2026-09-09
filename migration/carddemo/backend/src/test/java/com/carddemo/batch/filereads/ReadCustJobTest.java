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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-U1 to FR-U3: READCUST prints the customer file in customer-id order, twice per
 * record — the quirk of CBCUS01C displaying in both the read paragraph and the main
 * loop.
 */
@SpringBatchTest
@SpringBootTest(properties = "carddemo.batch.output-dir=target/batch-output/readcust-test")
class ReadCustJobTest {

    private static final Path OUTPUT_DIR = Path.of("target/batch-output/readcust-test");

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    private Job readCustJob;

    @BeforeEach
    void selectJob() {
        jobLauncherTestUtils.setJob(readCustJob);
    }

    @Test
    void printsEveryCustomerTwiceInKeyOrder() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", "readcust-" + System.nanoTime())
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<String> sysout = FileReadJobTestSupport.sysout(OUTPUT_DIR, "READCUST");
        assertThat(sysout.get(0)).isEqualTo("START OF EXECUTION OF PROGRAM CBCUS01C");
        assertThat(sysout.get(sysout.size() - 1)).isEqualTo("END OF EXECUTION OF PROGRAM CBCUS01C");

        List<String> images = sysout.subList(1, sysout.size() - 1);
        List<String> expected = new ArrayList<>();
        for (String record : FileReadJobTestSupport.fixture("custdata.txt",
                RecordImages.CUSTOMER_LENGTH).stream().sorted().toList()) {
            expected.add(record);
            expected.add(record);
        }
        assertThat(images).isEqualTo(expected);
        assertThat(images).allMatch(line -> line.length() == RecordImages.CUSTOMER_LENGTH);
    }
}
