package com.carddemo.batch.config;

import com.carddemo.batch.model.DailyTransaction;
import com.carddemo.batch.model.Transaction;
import com.carddemo.batch.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.transform.Range;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch job configuration for COMBTRAN.
 * Migrated from COMBTRAN.jcl.
 *
 * The original JCL sorts and combines two flat files then REPROs to VSAM.
 * In Java: reads system-generated transactions from a flat file and
 * merges/upserts into the Transaction table.
 */
@Configuration
public class CombineTransactionJobConfig {

    private static final Logger log = LoggerFactory.getLogger(CombineTransactionJobConfig.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final TransactionRepository transactionRepository;
    private final ResourceLoader resourceLoader;

    public CombineTransactionJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            TransactionRepository transactionRepository,
            ResourceLoader resourceLoader) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.transactionRepository = transactionRepository;
        this.resourceLoader = resourceLoader;
    }

    @Bean
    public Job combineTransactionJob() {
        return new JobBuilder("combineTransactionJob", jobRepository)
                .start(combineTransactionStep())
                .build();
    }

    @Bean
    public Step combineTransactionStep() {
        return new StepBuilder("combineTransactionStep", jobRepository)
                .<DailyTransaction, Transaction>chunk(100, transactionManager)
                .reader(combineTransactionReader(null))
                .processor(combineTransactionProcessor())
                .writer(combineTransactionWriter())
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<DailyTransaction> combineTransactionReader(
            @Value("#{jobParameters['inputFile'] ?: '${carddemo.files.combine-input:classpath:data/combtran.dat}'}") String filePath) {
        return new FlatFileItemReaderBuilder<DailyTransaction>()
                .name("combineTransactionReader")
                .resource(resourceLoader.getResource(filePath))
                .fixedLength()
                .columns(
                        new Range(1, 16),    // TRAN-ID
                        new Range(17, 18),   // TRAN-TYPE-CD
                        new Range(19, 22),   // TRAN-CAT-CD
                        new Range(23, 32),   // TRAN-SOURCE
                        new Range(33, 132),  // TRAN-DESC
                        new Range(133, 143), // TRAN-AMT
                        new Range(144, 152), // TRAN-MERCHANT-ID
                        new Range(153, 202), // TRAN-MERCHANT-NAME
                        new Range(203, 252), // TRAN-MERCHANT-CITY
                        new Range(253, 262), // TRAN-MERCHANT-ZIP
                        new Range(263, 278), // TRAN-CARD-NUM
                        new Range(279, 304), // TRAN-ORIG-TS
                        new Range(305, 330)  // TRAN-PROC-TS
                )
                .names("tranId", "tranTypeCd", "tranCatCd", "tranSource", "tranDesc",
                        "tranAmt", "merchantId", "merchantName", "merchantCity",
                        "merchantZip", "cardNum", "origTimestamp", "procTimestamp")
                .targetType(DailyTransaction.class)
                .build();
    }

    @Bean
    public ItemProcessor<DailyTransaction, Transaction> combineTransactionProcessor() {
        return item -> {
            Transaction tran = new Transaction();
            tran.setTranId(item.getTranId());
            tran.setTranTypeCd(item.getTranTypeCd());
            tran.setTranCatCd(item.getTranCatCd());
            tran.setTranSource(item.getTranSource());
            tran.setTranDesc(item.getTranDesc());
            tran.setTranAmt(item.getTranAmt());
            tran.setMerchantId(item.getMerchantId());
            tran.setMerchantName(item.getMerchantName());
            tran.setMerchantCity(item.getMerchantCity());
            tran.setMerchantZip(item.getMerchantZip());
            tran.setCardNum(item.getCardNum());
            tran.setOrigTimestamp(item.getOrigTimestamp());
            tran.setProcTimestamp(item.getProcTimestamp());
            return tran;
        };
    }

    @Bean
    public ItemWriter<Transaction> combineTransactionWriter() {
        return (Chunk<? extends Transaction> items) -> {
            for (Transaction tran : items) {
                transactionRepository.save(tran);
            }
            log.info("Combined {} transactions into Transaction table", items.size());
        };
    }
}
