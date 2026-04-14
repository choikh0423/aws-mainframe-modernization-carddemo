package com.carddemo.batch.config;

import com.carddemo.batch.model.Transaction;
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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.List;

/**
 * Spring Batch job configuration for TRANBKP.
 * Migrated from TRANBKP.jcl.
 *
 * The original JCL REPROs the VSAM file to a backup flat file,
 * then deletes and redefines the VSAM cluster.
 * In Java:
 *   Step 1: Export all Transaction records to a backup flat file
 *   Step 2: Truncate the transaction table
 */
@Configuration
public class TransactionBackupJobConfig {

    private static final Logger log = LoggerFactory.getLogger(TransactionBackupJobConfig.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final TransactionRepository transactionRepository;

    public TransactionBackupJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            TransactionRepository transactionRepository) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.transactionRepository = transactionRepository;
    }

    @Bean
    public Job transactionBackupJob() {
        return new JobBuilder("transactionBackupJob", jobRepository)
                .start(exportTransactionsStep(null))
                .next(truncateTransactionsStep())
                .build();
    }

    @Bean
    @StepScope
    public Step exportTransactionsStep(
            @Value("#{jobParameters['backupFile'] ?: '${carddemo.files.transaction-backup:output/tranbkup.dat}'}") String backupFile) {
        return new StepBuilder("exportTransactionsStep", jobRepository)
                .tasklet(exportTasklet(backupFile), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet exportTasklet(
            @Value("#{jobParameters['backupFile'] ?: '${carddemo.files.transaction-backup:output/tranbkup.dat}'}") String backupFile) {
        return (StepContribution contribution, ChunkContext chunkContext) -> {
            log.info("Exporting transactions to backup file: {}", backupFile);

            List<Transaction> allTransactions = transactionRepository.findAll();

            try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(backupFile)))) {
                for (Transaction tran : allTransactions) {
                    writer.printf("%-16s%-2s%04d%-10s%-100s%+012.2f%09d%-50s%-50s%-10s%-16s%-26s%-26s%-20s%n",
                            safe(tran.getTranId(), 16),
                            safe(tran.getTranTypeCd(), 2),
                            tran.getTranCatCd() != null ? tran.getTranCatCd() : 0,
                            safe(tran.getTranSource(), 10),
                            safe(tran.getTranDesc(), 100),
                            tran.getTranAmt() != null ? tran.getTranAmt().doubleValue() : 0.0,
                            tran.getMerchantId() != null ? tran.getMerchantId() : 0L,
                            safe(tran.getMerchantName(), 50),
                            safe(tran.getMerchantCity(), 50),
                            safe(tran.getMerchantZip(), 10),
                            safe(tran.getCardNum(), 16),
                            safe(tran.getOrigTimestamp(), 26),
                            safe(tran.getProcTimestamp(), 26),
                            "");
                }
            }

            log.info("Exported {} transactions to {}", allTransactions.size(), backupFile);
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step truncateTransactionsStep() {
        return new StepBuilder("truncateTransactionsStep", jobRepository)
                .tasklet((StepContribution contribution, ChunkContext chunkContext) -> {
                    long count = transactionRepository.count();
                    transactionRepository.deleteAllInBatch();
                    log.info("Truncated {} transactions from the transaction table", count);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    private static String safe(String value, int maxLen) {
        if (value == null) return "";
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }
}
