package com.carddemo.batch;

import com.carddemo.batch.model.Account;
import com.carddemo.batch.model.CardXref;
import com.carddemo.batch.model.Transaction;
import com.carddemo.batch.model.TransactionCategoryBalance;
import com.carddemo.batch.repository.AccountRepository;
import com.carddemo.batch.repository.CardXrefRepository;
import com.carddemo.batch.repository.TransactionCategoryBalanceRepository;
import com.carddemo.batch.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.io.FileWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the POSTTRAN job (PostTransactionJobConfig).
 * Tests the full pipeline: read daily transactions, validate, post to database.
 */
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class PostTransactionJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier("postTransactionJob")
    private Job postTransactionJob;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CardXrefRepository cardXrefRepository;

    @Autowired
    private TransactionCategoryBalanceRepository tcatbalRepository;

    private File dailyTranFile;

    @BeforeEach
    void setUp() throws Exception {
        jobLauncherTestUtils.setJob(postTransactionJob);

        // Create a test daily transaction file (350-byte fixed-length records)
        dailyTranFile = File.createTempFile("dailytran", ".dat");
        dailyTranFile.deleteOnExit();

        try (PrintWriter writer = new PrintWriter(new FileWriter(dailyTranFile))) {
            // Valid transaction: card 4111111111111111, type 01, cat 1, amount 50.00
            writer.printf("%-16s%-2s%04d%-10s%-100s%+011.2f%09d%-50s%-50s%-10s%-16s%-26s%-26s%n",
                    "0000000000000000", "01", 1, "POS",
                    "TEST PURCHASE",
                    50.00, 100000001, "TEST MERCHANT", "TEST CITY", "10001",
                    "4111111111111111",
                    "2024-01-20-10.30.00.000000",
                    "");
        }
    }

    @Test
    void testPostTransactionJobCompletes() throws Exception {
        long initialCount = transactionRepository.count();

        JobParameters params = new JobParametersBuilder()
                .addString("dailyTranFile", dailyTranFile.getAbsolutePath())
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
    }

    @Test
    void testSeedDataLoaded() {
        // Verify seed data from data.sql was loaded
        assertTrue(accountRepository.count() > 0, "Accounts should be loaded");
        assertTrue(cardXrefRepository.count() > 0, "CardXref records should be loaded");
        assertTrue(transactionRepository.count() > 0, "Transactions should be loaded");
    }

    @Test
    void testAccountExists() {
        Account account = accountRepository.findById(10000000001L).orElse(null);
        assertNotNull(account, "Account 10000000001 should exist");
        assertEquals("Y", account.getActiveStatus());
        assertNotNull(account.getCreditLimit());
    }

    @Test
    void testCardXrefExists() {
        CardXref xref = cardXrefRepository.findByCardNum("4111111111111111").orElse(null);
        assertNotNull(xref, "CardXref for card 4111111111111111 should exist");
        assertEquals(10000000001L, xref.getAcctId());
    }
}
