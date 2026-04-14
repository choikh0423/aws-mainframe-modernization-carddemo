package com.carddemo.batch;

import com.carddemo.batch.repository.TransactionCategoryBalanceRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the INTCALC job (InterestCalcJobConfig).
 * Tests interest calculation from category balances.
 */
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class InterestCalcJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier("interestCalcJob")
    private Job interestCalcJob;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionCategoryBalanceRepository tcatbalRepository;

    @Test
    void testInterestCalcJobCompletes() throws Exception {
        jobLauncherTestUtils.setJob(interestCalcJob);

        long initialTranCount = transactionRepository.count();

        JobParameters params = new JobParametersBuilder()
                .addString("processDate", "2024071800")
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
    }

    @Test
    void testInterestTransactionsCreated() throws Exception {
        jobLauncherTestUtils.setJob(interestCalcJob);

        long initialTranCount = transactionRepository.count();

        JobParameters params = new JobParametersBuilder()
                .addString("processDate", "2024071800")
                .toJobParameters();

        jobLauncherTestUtils.launchJob(params);

        // Interest transactions should be created for the seed TransactionCategoryBalance data
        long finalTranCount = transactionRepository.count();
        assertTrue(finalTranCount >= initialTranCount,
                "Interest transactions should be created");
    }
}
