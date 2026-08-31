package com.carddemo.batch;

import com.carddemo.batch.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the TRANREPT job (TransactionReportJobConfig).
 * Tests report generation from database transactions.
 */
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class TransactionReportJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier("transactionReportJob")
    private Job transactionReportJob;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void testTransactionReportJobCompletes() throws Exception {
        jobLauncherTestUtils.setJob(transactionReportJob);

        // Ensure output directory exists
        new File("output").mkdirs();

        JobParameters params = new JobParametersBuilder()
                .addString("startDate", "2024-01-01")
                .addString("endDate", "2024-12-31")
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
    }

    @Test
    void testReportFileCreated() throws Exception {
        jobLauncherTestUtils.setJob(transactionReportJob);

        new File("output").mkdirs();

        JobParameters params = new JobParametersBuilder()
                .addString("startDate", "2024-01-01")
                .addString("endDate", "2024-12-31")
                .toJobParameters();

        jobLauncherTestUtils.launchJob(params);

        File reportFile = new File("output/tranrept.txt");
        assertTrue(reportFile.exists(), "Report file should be created");
        assertTrue(reportFile.length() > 0, "Report file should not be empty");
    }
}
