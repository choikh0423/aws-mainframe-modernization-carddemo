package com.carddemo.batch.config;

import com.carddemo.batch.model.Account;
import com.carddemo.batch.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;

/**
 * Spring Batch job configuration for READACCT.
 * Migrated from CBACT01C.cbl and READACCT.jcl.
 *
 * Reads all Account entities sequentially and writes to a flat file.
 */
@Configuration
public class ReadAccountJobConfig {

    private static final Logger log = LoggerFactory.getLogger(ReadAccountJobConfig.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final AccountRepository accountRepository;

    public ReadAccountJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            AccountRepository accountRepository) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.accountRepository = accountRepository;
    }

    @Bean
    public Job readAccountJob() {
        return new JobBuilder("readAccountJob", jobRepository)
                .start(readAccountStep(null))
                .build();
    }

    @Bean
    @StepScope
    public Step readAccountStep(
            @Value("#{jobParameters['outputFile'] ?: 'output/accounts.dat'}") String outputFile) {
        return new StepBuilder("readAccountStep", jobRepository)
                .tasklet(readAccountTasklet(outputFile), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet readAccountTasklet(
            @Value("#{jobParameters['outputFile'] ?: 'output/accounts.dat'}") String outputFile) {
        return (StepContribution contribution, ChunkContext chunkContext) -> {
            log.info("START OF EXECUTION OF PROGRAM READACCT (CBACT01C)");

            List<Account> accounts = accountRepository.findAll();

            try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(outputFile)))) {
                for (Account acct : accounts) {
                    log.info("ACCT-ID: {} ACTIVE-STATUS: {} CURR-BAL: {} CREDIT-LIMIT: {}",
                            acct.getAcctId(), acct.getActiveStatus(),
                            acct.getCurrentBalance(), acct.getCreditLimit());
                    writer.printf("%011d%-1s%+013.2f%+013.2f%+013.2f%-10s%-10s%-10s%+013.2f%+013.2f%-10s%-10s%n",
                            acct.getAcctId() != null ? acct.getAcctId() : 0L,
                            acct.getActiveStatus() != null ? acct.getActiveStatus() : " ",
                            acct.getCurrentBalance() != null ? acct.getCurrentBalance().doubleValue() : 0.0,
                            acct.getCreditLimit() != null ? acct.getCreditLimit().doubleValue() : 0.0,
                            acct.getCashCreditLimit() != null ? acct.getCashCreditLimit().doubleValue() : 0.0,
                            acct.getOpenDate() != null ? acct.getOpenDate() : "",
                            acct.getExpirationDate() != null ? acct.getExpirationDate() : "",
                            acct.getReissueDate() != null ? acct.getReissueDate() : "",
                            acct.getCurrentCycleCredit() != null ? acct.getCurrentCycleCredit().doubleValue() : 0.0,
                            acct.getCurrentCycleDebit() != null ? acct.getCurrentCycleDebit().doubleValue() : 0.0,
                            acct.getAddressZip() != null ? acct.getAddressZip() : "",
                            acct.getGroupId() != null ? acct.getGroupId() : "");
                }
            }

            log.info("END OF EXECUTION OF PROGRAM READACCT - {} records", accounts.size());
            return RepeatStatus.FINISHED;
        };
    }
}
