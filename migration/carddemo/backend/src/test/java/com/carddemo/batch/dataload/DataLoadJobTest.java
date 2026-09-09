package com.carddemo.batch.dataload;

import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.CardRepository;
import com.carddemo.common.repository.CardXrefRepository;
import com.carddemo.common.repository.CustomerRepository;
import com.carddemo.common.repository.DailyTransactionRepository;
import com.carddemo.common.repository.DisclosureGroupRepository;
import com.carddemo.common.repository.TransactionCategoryBalanceRepository;
import com.carddemo.common.repository.TransactionCategoryRepository;
import com.carddemo.common.repository.TransactionTypeRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the DATALOAD job end to end against its own in-memory database, so the
 * PREDEL step does not disturb the seeded database the other tests share.
 */
@SpringBatchTest
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-dataload;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class DataLoadJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    // The context holds several jobs, so the one under test is named explicitly.
    @Autowired
    @Qualifier("dataLoadJob")
    private Job dataLoadJob;
    @Autowired
    @Qualifier("dataLoadJob")
    private Job dataLoadJob;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private CardRepository cards;
    @Autowired
    private CardXrefRepository cardXrefs;
    @Autowired
    private DailyTransactionRepository dailyTransactions;
    @Autowired
    private TransactionCategoryBalanceRepository categoryBalances;
    @Autowired
    private DisclosureGroupRepository disclosureGroups;
    @Autowired
    private TransactionTypeRepository transactionTypes;
    @Autowired
    private TransactionCategoryRepository transactionCategories;

    /** JobLauncherTestUtils cannot pick a job by type once a second stream registers one. */
    @BeforeEach
    void useTheDataLoadJob() {
        jobLauncherTestUtils.setJob(dataLoadJob);
    }

    @Test
    void loadsEveryMasterFileFromTheAsciiUnloads() throws Exception {
        jobLauncherTestUtils.setJob(dataLoadJob);
        JobExecution execution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", "dataload-test")
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions())
                .extracting(step -> step.getStepName())
                .containsExactly("PREDEL", "LOADACCT", "LOADCUST", "LOADCARD", "LOADXREF",
                        "LOADDLTR", "LOADTCAT", "LOADDISC", "LOADTRTP", "LOADTRCT");

        assertThat(accounts.count()).isEqualTo(50);
        assertThat(customers.count()).isEqualTo(50);
        assertThat(cards.count()).isEqualTo(50);
        assertThat(cardXrefs.count()).isEqualTo(50);
        assertThat(dailyTransactions.count()).isEqualTo(300);
        assertThat(categoryBalances.count()).isEqualTo(50);
        assertThat(disclosureGroups.count()).isEqualTo(51);
        assertThat(transactionTypes.count()).isEqualTo(7);
        assertThat(transactionCategories.count()).isEqualTo(18);
    }
}
