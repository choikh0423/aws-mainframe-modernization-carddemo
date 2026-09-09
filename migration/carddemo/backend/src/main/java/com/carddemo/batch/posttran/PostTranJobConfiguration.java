package com.carddemo.batch.posttran;

import com.carddemo.common.domain.DailyTransactionRecord;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.transform.PassThroughLineAggregator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Job POSTTRAN - S-11 DailyTransactionPosting, the migration of
 * {@code app/jcl/POSTTRAN.jcl} and its single step
 * {@code STEP15 EXEC PGM=CBTRN02C}.
 *
 * <p>The step reads DALYTRAN in transaction-id order, validates each record
 * against CCXREF and ACCTDAT, posts the valid ones to TRANSACT / TCATBALF /
 * ACCTDAT and writes the rejected ones to the DALYREJS generation this run
 * allocated. It is chunk oriented and restartable per D-3: the reader position
 * and the reject file offset are kept in the JobRepository, so a restart resumes
 * at the last committed chunk instead of reposting what was already committed.
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.profiles.active=postgres --spring.batch.job.name=POSTTRAN
 * </pre>
 *
 * <p>Condition codes follow the COBOL: 0 when nothing was rejected, 4 when
 * something was, 12 when the step abended (CBTRN02C.cbl:229-231).
 */
@Configuration
public class PostTranJobConfiguration {

    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;

    public PostTranJobConfiguration(JobRepository jobRepository,
                                    PlatformTransactionManager transactionManager,
                                    EntityManagerFactory entityManagerFactory) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.entityManagerFactory = entityManagerFactory;
    }

    @Bean
    public Job postTranJob(Step postTranStep15, PostTranJobListener postTranJobListener) {
        return new JobBuilder("POSTTRAN", jobRepository)
                .start(postTranStep15)
                .listener(postTranJobListener)
                .build();
    }

    /** STEP15 EXEC PGM=CBTRN02C (POSTTRAN.jcl:23). */
    @Bean
    public Step postTranStep15(DailyTransactionPostingProcessor postingProcessor,
                               ItemWriter<String> postTranRejectWriter,
                               TransactionMasterInitializer transactionMasterInitializer) {
        return new StepBuilder("STEP15", jobRepository)
                .<DailyTransactionRecord, String>chunk(CHUNK_SIZE, transactionManager)
                .reader(dailyTransactionReader())
                .processor(postingProcessor)
                .writer(postTranRejectWriter)
                .listener(transactionMasterInitializer)
                .build();
    }

    /**
     * DALYTRAN, read sequentially. The legacy file is a QSAM dataset in
     * transaction-id order, which the ordered query reproduces.
     */
    @Bean
    public ItemReader<DailyTransactionRecord> dailyTransactionReader() {
        return new JpaPagingItemReaderBuilder<DailyTransactionRecord>()
                .name("dailyTransactionReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("select d from DailyTransactionRecord d order by d.id")
                .pageSize(CHUNK_SIZE)
                .build();
    }

    /**
     * DALYREJS, RECFM=F LRECL=430 (POSTTRAN.jcl:34-38). The generation is chosen
     * once per job instance by {@link PostTranJobListener} and passed in through
     * the job execution context so restarts append to the same file.
     */
    @Bean
    @StepScope
    public FlatFileItemWriter<String> postTranRejectWriter(
            @Value("#{jobExecutionContext['" + PostTranJobListener.REJECT_FILE_KEY + "']}") String rejectFile) {
        return new FlatFileItemWriterBuilder<String>()
                .name("postTranRejectWriter")
                .resource(new FileSystemResource(rejectFile))
                .lineAggregator(new PassThroughLineAggregator<>())
                .shouldDeleteIfEmpty(false)
                .build();
    }
}
