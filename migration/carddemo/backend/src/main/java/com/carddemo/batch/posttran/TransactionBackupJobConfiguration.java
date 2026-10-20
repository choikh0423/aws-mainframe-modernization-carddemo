package com.carddemo.batch.posttran;

import com.carddemo.common.domain.TransactionRecord;
import com.carddemo.common.repository.TransactionRepository;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.transform.PassThroughLineAggregator;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;

/**
 * Job TRANBKP - the migration of {@code app/jcl/TRANBKP.jcl}, "REPRO and Delete
 * Transaction Master", the job Control-M runs daily in
 * {@code DAILY-TransactionBackup} before the posting run.
 *
 * <table>
 *   <caption>JCL steps</caption>
 *   <tr><th>Legacy</th><th>Migrated</th></tr>
 *   <tr><td>{@code STEP05R EXEC PROC=REPROC} - {@code REPRO} TRANSACT KSDS to
 *       {@code TRANSACT.BKUP(+1)}, LRECL 350</td>
 *       <td>step {@code STEP05R}: {@code transactions} in key order to the next
 *       {@code TRANSACT.BKUP.G<nnnn>V00} file, one 350-byte CVTRA05Y image per row</td></tr>
 *   <tr><td>{@code STEP05 EXEC PGM=IDCAMS} - {@code DELETE} the cluster and its AIX</td>
 *       <td>step {@code STEP05}: delete every {@code transactions} row</td></tr>
 *   <tr><td>{@code STEP10 EXEC PGM=IDCAMS} - {@code DEFINE} the cluster again, empty</td>
 *       <td>nothing: the table and its {@code proc_ts} index (TRANIDX, {@code V1100})
 *       are schema, they survive the delete</td></tr>
 * </table>
 *
 * <p>One deliberate deviation: {@code STEP05} carries no {@code COND}, so on z/OS a
 * REPRO that ends with a non-zero code without abending still lets the DELETE run
 * and the master is lost without a backup. Here {@code STEP05} only runs once
 * {@code STEP05R} has completed.
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.profiles.active=postgres --spring.batch.job.name=TRANBKP
 * </pre>
 */
@Configuration
public class TransactionBackupJobConfiguration {

    /** Job execution context key holding the TRANSACT.BKUP generation of this run. */
    public static final String BACKUP_FILE_KEY = "tranbkp.backup.file";

    private static final int CHUNK_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(TransactionBackupJobConfiguration.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    public TransactionBackupJobConfiguration(JobRepository jobRepository,
                                             PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
    }

    @Bean
    public Job transactionBackupJob(Step tranBkpStep05R, Step tranBkpStep05,
                                    TransactionBackupGenerations backupGenerations) {
        return new JobBuilder("TRANBKP", jobRepository)
                .listener(allocateBackupGeneration(backupGenerations))
                .start(tranBkpStep05R)
                .next(tranBkpStep05)
                .build();
    }

    /** STEP05R EXEC PROC=REPROC (TRANBKP.jcl:23-33). */
    @Bean
    public Step tranBkpStep05R(JpaPagingItemReader<TransactionRecord> transactionMasterReader,
                               FlatFileItemWriter<String> transactionBackupWriter) {
        return new StepBuilder("STEP05R", jobRepository)
                .<TransactionRecord, String>chunk(CHUNK_SIZE, transactionManager)
                .reader(transactionMasterReader)
                .processor(TransactionMasterImage::render)
                .writer(transactionBackupWriter)
                .listener(reproCompletion())
                .build();
    }

    /** STEP05 EXEC PGM=IDCAMS, DELETE ... CLUSTER / ALTERNATEINDEX (TRANBKP.jcl:37-46). */
    @Bean
    public Step tranBkpStep05(TransactionRepository transactionRepository) {
        return new StepBuilder("STEP05", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    long deleted = transactionRepository.count();
                    transactionRepository.deleteAllInBatch();
                    contribution.incrementWriteCount(deleted);
                    log.info("IDC0550I ENTRY (C) AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS DELETED - {} RECORDS",
                            deleted);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /** FILEIN DD DSN=AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS, read in key sequence. */
    @Bean
    @StepScope
    public JpaPagingItemReader<TransactionRecord> transactionMasterReader(EntityManagerFactory entityManagerFactory) {
        return new JpaPagingItemReaderBuilder<TransactionRecord>()
                .name("transactionMasterReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("select t from TransactionRecord t order by t.id")
                .pageSize(CHUNK_SIZE)
                .build();
    }

    /** FILEOUT DD DSN=AWS.M2.CARDDEMO.TRANSACT.BKUP(+1), DCB=(LRECL=350,RECFM=FB). */
    @Bean
    @StepScope
    public FlatFileItemWriter<String> transactionBackupWriter(
            @Value("#{jobExecutionContext['" + BACKUP_FILE_KEY + "']}") String backupFile) {
        return new FlatFileItemWriterBuilder<String>()
                .name("transactionBackupWriter")
                .resource(new FileSystemResource(backupFile))
                .lineAggregator(new PassThroughLineAggregator<>())
                .shouldDeleteIfEmpty(false)
                .build();
    }

    /**
     * {@code DISP=(NEW,CATLG,DELETE)}: one new generation per job instance. The
     * name is kept in the job execution context so a restart writes into the
     * generation the failed run allocated instead of rolling another.
     */
    private static JobExecutionListener allocateBackupGeneration(TransactionBackupGenerations generations) {
        return new JobExecutionListener() {
            @Override
            public void beforeJob(JobExecution jobExecution) {
                if (!jobExecution.getExecutionContext().containsKey(BACKUP_FILE_KEY)) {
                    Path generation = generations.allocateNextGeneration();
                    jobExecution.getExecutionContext().putString(BACKUP_FILE_KEY, generation.toString());
                    log.info("TRANSACT.BKUP(+1) allocated as {}", generation.getFileName());
                }
            }
        };
    }

    /** The IDCAMS REPRO completion message. */
    private static StepExecutionListener reproCompletion() {
        return new StepExecutionListener() {
            @Override
            public ExitStatus afterStep(StepExecution stepExecution) {
                log.info("IDC0005I NUMBER OF RECORDS PROCESSED WAS {}", stepExecution.getWriteCount());
                return stepExecution.getExitStatus();
            }
        };
    }
}
