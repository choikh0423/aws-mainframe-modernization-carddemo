package com.carddemo.batch.config;

import com.carddemo.batch.model.Transaction;
import com.carddemo.batch.model.TransactionCategoryBalance;
import com.carddemo.batch.processor.InterestCalcProcessor;
import com.carddemo.batch.repository.AccountRepository;
import com.carddemo.batch.repository.TransactionCategoryBalanceRepository;
import com.carddemo.batch.repository.TransactionRepository;
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

import java.math.BigDecimal;
import java.util.List;

/**
 * Spring Batch job configuration for INTCALC.
 * Migrated from CBACT04C.cbl and INTCALC.jcl.
 *
 * Reads all TransactionCategoryBalance records, computes interest,
 * creates system-generated Transaction records, and updates Account balances.
 * Accepts date parameter (the JCL passes PARM='2022071800').
 */
@Configuration
public class InterestCalcJobConfig {

    private static final Logger log = LoggerFactory.getLogger(InterestCalcJobConfig.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final InterestCalcProcessor interestCalcProcessor;
    private final TransactionCategoryBalanceRepository tcatbalRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public InterestCalcJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            InterestCalcProcessor interestCalcProcessor,
            TransactionCategoryBalanceRepository tcatbalRepository,
            TransactionRepository transactionRepository,
            AccountRepository accountRepository) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.interestCalcProcessor = interestCalcProcessor;
        this.tcatbalRepository = tcatbalRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    @Bean
    public Job interestCalcJob() {
        return new JobBuilder("interestCalcJob", jobRepository)
                .start(interestCalcStep(null))
                .build();
    }

    @Bean
    @StepScope
    public Step interestCalcStep(
            @Value("#{jobParameters['processDate']}") String processDate) {
        return new StepBuilder("interestCalcStep", jobRepository)
                .tasklet(interestCalcTasklet(processDate), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet interestCalcTasklet(
            @Value("#{jobParameters['processDate']}") String processDate) {
        return (StepContribution contribution, ChunkContext chunkContext) -> {
            String dateParam = processDate != null ? processDate : "00000000";
            // Extract first 8 characters (PARM='2022071800' -> '20220718')
            if (dateParam.length() > 8) {
                dateParam = dateParam.substring(0, 8);
            }

            log.info("Starting interest calculation with process date: {}", dateParam);
            interestCalcProcessor.setProcessDate(dateParam);

            List<TransactionCategoryBalance> allBalances = tcatbalRepository.findAll();
            log.info("Processing {} TransactionCategoryBalance records", allBalances.size());

            BigDecimal totalInterest = BigDecimal.ZERO;
            int transactionsCreated = 0;

            for (TransactionCategoryBalance tcatbal : allBalances) {
                Transaction interestTran = interestCalcProcessor.process(tcatbal);
                if (interestTran != null) {
                    transactionRepository.save(interestTran);
                    totalInterest = totalInterest.add(interestTran.getTranAmt());
                    transactionsCreated++;
                }
            }

            log.info("Interest calculation complete: {} transactions created, total interest: {}",
                    transactionsCreated, totalInterest);

            return RepeatStatus.FINISHED;
        };
    }
}
