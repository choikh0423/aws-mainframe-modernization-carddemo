package com.carddemo.batch.tranreport;

import com.carddemo.common.repository.TransactionCategoryBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** PRTCATBL end to end over the seeded TCATBALF data (app/data/ASCII/tcatbal.txt). */
@SpringBatchTest
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-prtcatbl;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "carddemo.batch.output-dir=${java.io.tmpdir}/carddemo-prtcatbl-test"
})
class PrintCategoryBalanceJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    // The context holds several jobs, so the one under test is named explicitly.
    @Autowired
    @Qualifier("printCategoryBalanceJob")
    private Job printCategoryBalanceJob;
    @Autowired
    private TransactionCategoryBalanceRepository balances;

    @BeforeEach
    void useTheCategoryBalanceJob() {
        jobLauncherTestUtils.setJob(printCategoryBalanceJob);
    }

    /** The three JCL steps, the whole file unloaded, and the report sorted by key. */
    @Test
    void runsTheThreeJclStepsAndPrintsEveryCategoryBalance() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", String.valueOf(System.nanoTime()))
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions())
                .extracting(step -> step.getStepName())
                .containsExactly("DELDEF", "STEP05R", "STEP10R");

        long expected = balances.count();
        assertThat(lines(execution, PrintCategoryBalanceJobConfiguration.BACKUP_FILE))
                .hasSize((int) expected)
                .allMatch(line -> line.length() == CategoryBalanceRecordImage.LENGTH);

        List<String> report = lines(execution, PrintCategoryBalanceJobConfiguration.REPORT_FILE);
        assertThat(report).hasSize((int) expected);
        assertThat(report).allMatch(line -> line.length() == 41);
        assertThat(report).isSortedAccordingTo(Comparator.comparing(line -> line.substring(0, 19)));
        assertThat(report.get(0)).matches("\\d{11} \\d{2} \\d{4} \\d{9}\\.\\d{2} {9}");
    }

    /**
     * DELDEF - {@code IEFBR14} with {@code DISP=(MOD,DELETE)} on TCATBALF.REPT
     * (PRTCATBL.jcl:21-25): the previous report is gone before the run starts,
     * so a run never appends to it.
     */
    @Test
    void deletesThePreviousReportBeforeWritingANewOne() throws Exception {
        JobExecution first = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", String.valueOf(System.nanoTime()))
                .toJobParameters());
        Path report = Path.of(first.getExecutionContext()
                .getString(PrintCategoryBalanceJobConfiguration.REPORT_FILE));
        Files.write(report, List.of("stale report content"), StandardCharsets.UTF_8);

        JobExecution second = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", String.valueOf(System.nanoTime()))
                .toJobParameters());

        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(Files.readAllLines(report, StandardCharsets.UTF_8))
                .hasSize((int) balances.count())
                .doesNotContain("stale report content");
    }

    private static List<String> lines(JobExecution execution, String key) throws IOException {
        return Files.readAllLines(Path.of(execution.getExecutionContext().getString(key)),
                StandardCharsets.UTF_8);
    }
}
