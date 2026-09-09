package com.carddemo.batch.filereads;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-G6: an empty input file gives file status 10 on the first read, so the job prints
 * only its two execution messages and completes normally. The context runs on an
 * unseeded database — the schema migration without the ASCII seed.
 */
@SpringBatchTest
@SpringBootTest(properties = {
        "carddemo.batch.output-dir=target/batch-output/empty-test",
        "spring.datasource.url=jdbc:h2:mem:carddemo-empty;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.flyway.locations=classpath:db/migration"})
class EmptyInputJobTest {

    private static final Path OUTPUT_DIR = Path.of("target/batch-output/empty-test");

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    private Job readCustJob;
    @Autowired
    private Job readAcctJob;

    @Test
    void printsOnlyTheExecutionMessagesForAnEmptyCustomerFile() throws Exception {
        run(readCustJob, "readcust-empty");

        assertThat(FileReadJobTestSupport.sysout(OUTPUT_DIR, "READCUST")).containsExactly(
                "START OF EXECUTION OF PROGRAM CBCUS01C",
                "END OF EXECUTION OF PROGRAM CBCUS01C");
    }

    @Test
    void writesNoExtractRecordsForAnEmptyAccountFile() throws Exception {
        run(readAcctJob, "readacct-empty");

        assertThat(FileReadJobTestSupport.sysout(OUTPUT_DIR, "READACCT")).containsExactly(
                "START OF EXECUTION OF PROGRAM CBACT01C",
                "END OF EXECUTION OF PROGRAM CBACT01C");
        for (String dataset : List.of("AWS.M2.CARDDEMO.ACCTDATA.PSCOMP",
                "AWS.M2.CARDDEMO.ACCTDATA.ARRYPS", "AWS.M2.CARDDEMO.ACCTDATA.VBPS")) {
            assertThat(Files.size(OUTPUT_DIR.resolve(dataset))).isZero();
        }
    }

    private void run(Job job, String run) throws Exception {
        jobLauncherTestUtils.setJob(job);
        JobExecution execution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", run + "-" + System.nanoTime())
                .toJobParameters());
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }
}
