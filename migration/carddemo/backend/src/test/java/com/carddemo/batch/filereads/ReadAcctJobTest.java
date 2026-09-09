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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-A1 to FR-A12: READACCT runs IEFBR14 then CBACT01C, printing every account and
 * writing the three sequential output datasets.
 */
@SpringBatchTest
@SpringBootTest(properties = "carddemo.batch.output-dir=target/batch-output/readacct-test")
class ReadAcctJobTest {

    private static final Path OUTPUT_DIR = Path.of("target/batch-output/readacct-test");
    private static final int ACCOUNTS = 50;

    /** 11 labelled fields, the separator, the two VBRC lines and the record image. */
    private static final int LINES_PER_ACCOUNT = 15;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    private Job readAcctJob;

    @BeforeEach
    void selectJob() {
        jobLauncherTestUtils.setJob(readAcctJob);
    }

    @Test
    void printsEveryAccountAndWritesTheThreeOutputDatasets() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", "readacct-" + System.nanoTime())
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions()).extracting(step -> step.getStepName())
                .containsExactly("PREDEL", "STEP05");

        List<String> sysout = FileReadJobTestSupport.sysout(OUTPUT_DIR, "READACCT");
        assertThat(sysout).hasSize(2 + ACCOUNTS * LINES_PER_ACCOUNT);
        assertThat(sysout.get(0)).isEqualTo("START OF EXECUTION OF PROGRAM CBACT01C");
        assertThat(sysout.get(sysout.size() - 1)).isEqualTo("END OF EXECUTION OF PROGRAM CBACT01C");

        // The first account of app/data/ASCII/acctdata.txt, field by field.
        assertThat(sysout.subList(1, 13)).containsExactly(
                "ACCT-ID                 :00000000001",
                "ACCT-ACTIVE-STATUS      :Y",
                "ACCT-CURR-BAL           :00000001940{",
                "ACCT-CREDIT-LIMIT       :00000020200{",
                "ACCT-CASH-CREDIT-LIMIT  :00000010200{",
                "ACCT-OPEN-DATE          :2014-11-20",
                "ACCT-EXPIRAION-DATE     :2025-05-20",
                "ACCT-REISSUE-DATE       :2025-05-20",
                "ACCT-CURR-CYC-CREDIT    :00000000000{",
                "ACCT-CURR-CYC-DEBIT     :00000000000{",
                "ACCT-GROUP-ID           :          ",
                "-".repeat(49));
        assertThat(sysout.get(13)).isEqualTo("VBRC-REC1:00000000001Y");
        assertThat(sysout.get(14)).isEqualTo("VBRC-REC2:00000000001" + "00000001940{"
                + "00000020200{" + "2025");

        List<String> fixture = FileReadJobTestSupport.fixture("acctdata.txt",
                RecordImages.ACCOUNT_LENGTH).stream().sorted().toList();
        assertThat(sysout.get(15)).isEqualTo(fixture.get(0));
        // Every printed record image is one of the unload's, in key order.
        List<String> printed = java.util.stream.IntStream.range(0, ACCOUNTS)
                .mapToObj(i -> sysout.get(15 + i * LINES_PER_ACCOUNT))
                .toList();
        assertThat(printed).isEqualTo(fixture);
    }

    @Test
    void writesFixedAndVariableLengthOutputRecords() throws Exception {
        jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", "readacct-out-" + System.nanoTime())
                .toJobParameters());

        assertThat(Files.size(OUTPUT_DIR.resolve("AWS.M2.CARDDEMO.ACCTDATA.PSCOMP")))
                .isEqualTo(107L * ACCOUNTS);
        assertThat(Files.size(OUTPUT_DIR.resolve("AWS.M2.CARDDEMO.ACCTDATA.ARRYPS")))
                .isEqualTo(110L * ACCOUNTS);
        // Each account writes a 12-byte and a 39-byte record, both with a 4-byte RDW.
        assertThat(Files.size(OUTPUT_DIR.resolve("AWS.M2.CARDDEMO.ACCTDATA.VBPS")))
                .isEqualTo((12L + 4 + 39 + 4) * ACCOUNTS);

        byte[] variable = Files.readAllBytes(OUTPUT_DIR.resolve("AWS.M2.CARDDEMO.ACCTDATA.VBPS"));
        assertThat(new byte[]{variable[0], variable[1], variable[2], variable[3]})
                .containsExactly(0x00, 0x10, 0x00, 0x00);
    }

    @Test
    void deletesThePreviousOutputDatasetsInThePredeleteStep() throws Exception {
        Files.createDirectories(OUTPUT_DIR);
        Path stale = OUTPUT_DIR.resolve("AWS.M2.CARDDEMO.ACCTDATA.PSCOMP");
        Files.write(stale, new byte[]{1, 2, 3});

        jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", "readacct-predel-" + System.nanoTime())
                .toJobParameters());

        assertThat(Files.size(stale)).isEqualTo(107L * ACCOUNTS);
    }
}
