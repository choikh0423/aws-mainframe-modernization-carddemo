package com.carddemo.batch.tranreport;

import com.carddemo.common.domain.TransactionCategoryBalanceRecord;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.transform.PassThroughLineAggregator;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Job PRTCATBL - print the transaction category balance file
 * (app/jcl/PRTCATBL.jcl), one {@link Step} per {@code EXEC PGM=}:
 *
 * <table>
 *   <caption>JCL steps</caption>
 *   <tr><th>JCL step</th><th>Program</th><th>Step</th></tr>
 *   <tr><td>DELDEF</td><td>IEFBR14 with {@code DISP=(MOD,DELETE)}</td><td>{@code DELDEF}</td></tr>
 *   <tr><td>STEP05R</td><td>IDCAMS REPRO (PROC REPROC)</td><td>{@code STEP05R} - unload TCATBALF</td></tr>
 *   <tr><td>STEP10R</td><td>SORT</td><td>{@code STEP10R} - sort and reformat</td></tr>
 * </table>
 *
 * <p>The job takes no parameters: the JCL prints the whole file. It is launched
 * the same way as any other job in this application:
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.batch.job.name=PRTCATBL
 * </pre>
 */
@Configuration
public class PrintCategoryBalanceJobConfiguration {

    public static final String JOB_NAME = "PRTCATBL";

    static final String BACKUP_FILE = "prtcatbl.backupFile";
    static final String REPORT_FILE = "prtcatbl.reportFile";

    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final Path outputDir;

    public PrintCategoryBalanceJobConfiguration(JobRepository jobRepository,
                                                PlatformTransactionManager transactionManager,
                                                EntityManagerFactory entityManagerFactory,
                                                @Value("${carddemo.batch.output-dir:${java.io.tmpdir}/carddemo-batch}")
                                                String outputDir) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.entityManagerFactory = entityManagerFactory;
        this.outputDir = Paths.get(outputDir);
    }

    @Bean
    public Job printCategoryBalanceJob(@Qualifier("categoryBalanceDeleteStep") Step categoryBalanceDeleteStep,
                                       @Qualifier("categoryBalanceUnloadStep") Step categoryBalanceUnloadStep,
                                       @Qualifier("categoryBalanceSortStep") Step categoryBalanceSortStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(categoryBalanceFiles())
                .start(categoryBalanceDeleteStep)
                .next(categoryBalanceUnloadStep)
                .next(categoryBalanceSortStep)
                .build();
    }

    /**
     * The unload is a new generation per run ({@code TCATBALF.BKUP(+1)},
     * PRTCATBL.jcl:39) while the report is a fixed name (PRTCATBL.jcl:63) that
     * DELDEF removes first.
     */
    private JobExecutionListener categoryBalanceFiles() {
        return new JobExecutionListener() {
            @Override
            public void beforeJob(JobExecution jobExecution) {
                try {
                    Files.createDirectories(outputDir);
                } catch (IOException e) {
                    throw new UncheckedIOException("Cannot create the batch output directory " + outputDir, e);
                }
                jobExecution.getExecutionContext().putString(BACKUP_FILE, outputDir
                        .resolve(String.format("TCATBALF.BKUP.G%04d", jobExecution.getId())).toString());
                jobExecution.getExecutionContext().putString(REPORT_FILE,
                        outputDir.resolve("TCATBALF.REPT").toString());
            }
        };
    }

    /**
     * DELDEF - {@code EXEC PGM=IEFBR14} with {@code DISP=(MOD,DELETE)} on
     * TCATBALF.REPT (app/jcl/PRTCATBL.jcl:21-25): allocate the report if it does
     * not exist, then delete it, so the run always starts from no report at all.
     */
    @Bean
    public Step categoryBalanceDeleteStep(
            @Qualifier("categoryBalanceDeleteTasklet") Tasklet categoryBalanceDeleteTasklet) {
        return new StepBuilder("DELDEF", jobRepository)
                .tasklet(categoryBalanceDeleteTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet categoryBalanceDeleteTasklet(
            @Value("#{jobExecutionContext['" + REPORT_FILE + "']}") String reportFile) {
        return (contribution, chunkContext) -> {
            Files.deleteIfExists(Paths.get(reportFile));
            return RepeatStatus.FINISHED;
        };
    }

    /**
     * STEP05R - {@code EXEC PROC=REPROC} (app/jcl/PRTCATBL.jcl:29-39): IDCAMS
     * REPRO of TCATBALF.VSAM.KSDS to a flat 50-byte backup, in key order.
     */
    @Bean
    public Step categoryBalanceUnloadStep(
            @Qualifier("categoryBalanceUnloadReader")
            JpaPagingItemReader<TransactionCategoryBalanceRecord> categoryBalanceUnloadReader,
            @Qualifier("categoryBalanceUnloadWriter") FlatFileItemWriter<String> categoryBalanceUnloadWriter) {
        return new StepBuilder("STEP05R", jobRepository)
                .<TransactionCategoryBalanceRecord, String>chunk(CHUNK_SIZE, transactionManager)
                .reader(categoryBalanceUnloadReader)
                .processor((ItemProcessor<TransactionCategoryBalanceRecord, String>) CategoryBalanceRecordImage::of)
                .writer(categoryBalanceUnloadWriter)
                .build();
    }

    @Bean
    @StepScope
    public JpaPagingItemReader<TransactionCategoryBalanceRecord> categoryBalanceUnloadReader() {
        return new JpaPagingItemReaderBuilder<TransactionCategoryBalanceRecord>()
                .name("categoryBalanceUnloadReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("select b from TransactionCategoryBalanceRecord b"
                        + " order by b.id.acctId, b.id.typeCd, b.id.catCd")
                .pageSize(CHUNK_SIZE)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemWriter<String> categoryBalanceUnloadWriter(
            @Value("#{jobExecutionContext['" + BACKUP_FILE + "']}") String backupFile) {
        return new FlatFileItemWriterBuilder<String>()
                .name("categoryBalanceUnloadWriter")
                .resource(new FileSystemResource(backupFile))
                .lineAggregator(new PassThroughLineAggregator<>())
                .build();
    }

    /** STEP10R - {@code EXEC PGM=SORT} (app/jcl/PRTCATBL.jcl:43-63). */
    @Bean
    public Step categoryBalanceSortStep(
            @Qualifier("categoryBalanceSortTasklet") Tasklet categoryBalanceSortTasklet) {
        return new StepBuilder("STEP10R", jobRepository)
                .tasklet(categoryBalanceSortTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet categoryBalanceSortTasklet(
            @Value("#{jobExecutionContext['" + BACKUP_FILE + "']}") String backupFile,
            @Value("#{jobExecutionContext['" + REPORT_FILE + "']}") String reportFile) {
        return new CategoryBalanceSortTasklet(Paths.get(backupFile), Paths.get(reportFile));
    }
}
