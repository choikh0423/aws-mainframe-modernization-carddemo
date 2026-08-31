package com.carddemo.batch.config;

import com.carddemo.batch.listener.JobCompletionListener;
import com.carddemo.batch.model.DailyTransaction;
import com.carddemo.batch.model.Transaction;
import com.carddemo.batch.processor.TransactionPostProcessor;
import com.carddemo.batch.processor.TransactionValidationProcessor;
import com.carddemo.batch.writer.RejectRecordWriter;
import com.carddemo.batch.writer.TransactionPostWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.transform.Range;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;

/**
 * Spring Batch job configuration for POSTTRAN.
 * Migrated from CBTRN02C.cbl and POSTTRAN.jcl.
 *
 * Reads daily transaction flat file (350-byte fixed-length records),
 * validates each transaction, posts valid ones to the database,
 * and writes rejected records to a reject file.
 */
@Configuration
public class PostTransactionJobConfig {

    private static final Logger log = LoggerFactory.getLogger(PostTransactionJobConfig.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final TransactionValidationProcessor validationProcessor;
    private final TransactionPostProcessor postProcessor;
    private final TransactionPostWriter transactionPostWriter;
    private final RejectRecordWriter rejectRecordWriter;
    private final JobCompletionListener jobCompletionListener;
    private final ResourceLoader resourceLoader;

    public PostTransactionJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            TransactionValidationProcessor validationProcessor,
            TransactionPostProcessor postProcessor,
            TransactionPostWriter transactionPostWriter,
            RejectRecordWriter rejectRecordWriter,
            JobCompletionListener jobCompletionListener,
            ResourceLoader resourceLoader) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.validationProcessor = validationProcessor;
        this.postProcessor = postProcessor;
        this.transactionPostWriter = transactionPostWriter;
        this.rejectRecordWriter = rejectRecordWriter;
        this.jobCompletionListener = jobCompletionListener;
        this.resourceLoader = resourceLoader;
    }

    @Bean
    public Job postTransactionJob() {
        return new JobBuilder("postTransactionJob", jobRepository)
                .start(postTransactionStep())
                .listener(jobCompletionListener)
                .build();
    }

    @Bean
    public Step postTransactionStep() {
        return new StepBuilder("postTransactionStep", jobRepository)
                .<DailyTransaction, Transaction>chunk(10, transactionManager)
                .reader(dailyTransactionReader(null))
                .processor(compositePostProcessor())
                .writer(transactionPostWriter)
                .faultTolerant()
                .skip(TransactionValidationProcessor.ValidationException.class)
                .skipLimit(Integer.MAX_VALUE)
                .listener(skipListener())
                .build();
    }

    /**
     * Reads 350-byte fixed-length daily transaction records.
     * Maps to COBOL copybook CVTRA06Y.cpy layout.
     * Replaces JCL DD DALYTRAN.
     */
    @Bean
    @StepScope
    public FlatFileItemReader<DailyTransaction> dailyTransactionReader(
            @Value("#{jobParameters['dailyTranFile'] ?: '${carddemo.files.daily-transaction:classpath:data/dailytran.dat}'}") String filePath) {
        return new FlatFileItemReaderBuilder<DailyTransaction>()
                .name("dailyTransactionReader")
                .resource(resourceLoader.getResource(filePath))
                .fixedLength()
                .columns(
                        new Range(1, 16),    // TRAN-ID           PIC X(16)
                        new Range(17, 18),   // TRAN-TYPE-CD      PIC X(02)
                        new Range(19, 22),   // TRAN-CAT-CD       PIC 9(04)
                        new Range(23, 32),   // TRAN-SOURCE       PIC X(10)
                        new Range(33, 132),  // TRAN-DESC         PIC X(100)
                        new Range(133, 143), // TRAN-AMT          PIC S9(09)V99
                        new Range(144, 152), // TRAN-MERCHANT-ID  PIC 9(09)
                        new Range(153, 202), // TRAN-MERCHANT-NAME PIC X(50)
                        new Range(203, 252), // TRAN-MERCHANT-CITY PIC X(50)
                        new Range(253, 262), // TRAN-MERCHANT-ZIP PIC X(10)
                        new Range(263, 278), // TRAN-CARD-NUM     PIC X(16)
                        new Range(279, 304), // TRAN-ORIG-TS      PIC X(26)
                        new Range(305, 330)  // TRAN-PROC-TS      PIC X(26)
                )
                .names("tranId", "tranTypeCd", "tranCatCd", "tranSource", "tranDesc",
                        "tranAmt", "merchantId", "merchantName", "merchantCity",
                        "merchantZip", "cardNum", "origTimestamp", "procTimestamp")
                .targetType(DailyTransaction.class)
                .build();
    }

    /**
     * Composite processor: first validate, then transform to Transaction entity.
     */
    @Bean
    public ItemProcessor<DailyTransaction, Transaction> compositePostProcessor() {
        return item -> {
            DailyTransaction validated = validationProcessor.process(item);
            if (validated == null) {
                return null;
            }
            return postProcessor.process(validated);
        };
    }

    /**
     * Skip listener that writes rejected transactions to the reject file.
     */
    @Bean
    public SkipListener<DailyTransaction, Transaction> skipListener() {
        return new SkipListener<>() {
            @Override
            public void onSkipInProcess(DailyTransaction item, Throwable t) {
                if (t instanceof TransactionValidationProcessor.ValidationException ve) {
                    rejectRecordWriter.writeReject(ve.toRejectRecord());
                    log.info("Rejected transaction for card {}: {} - {}",
                            item.getCardNum(), ve.getReasonCode(), ve.getReasonDescription());
                }
            }
        };
    }
}
