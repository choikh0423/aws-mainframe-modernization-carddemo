package com.carddemo.batch.tranreport;

import com.carddemo.common.batch.AbendService;
import com.carddemo.common.domain.TransactionRecord;
import com.carddemo.common.repository.CardXrefRepository;
import com.carddemo.common.repository.TransactionCategoryRepository;
import com.carddemo.common.repository.TransactionTypeRepository;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.DefaultJobParametersValidator;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.mapping.PassThroughLineMapper;
import org.springframework.batch.item.file.transform.PassThroughLineAggregator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Job TRANREPT - the daily transaction report (app/jcl/TRANREPT.jcl, whose steps
 * app/proc/TRANREPT.prc repeats verbatim), one {@link Step} per {@code EXEC PGM=}:
 *
 * <table>
 *   <caption>JCL steps</caption>
 *   <tr><th>JCL step</th><th>Program</th><th>Step</th></tr>
 *   <tr><td>STEP05R</td><td>IDCAMS REPRO (PROC REPROC)</td><td>{@code STEP05R} - unload TRANSACT</td></tr>
 *   <tr><td>STEP05R</td><td>SORT</td><td>{@code STEP05RSORT} - filter by date, sort by card</td></tr>
 *   <tr><td>STEP10R</td><td>CBTRN03C</td><td>{@code STEP10R} - write the report</td></tr>
 * </table>
 *
 * <p>The JCL names its first two steps identically (both {@code STEP05R},
 * TRANREPT.jcl:22 and :37); Spring Batch keys restart state by step name, so the
 * SORT step is named {@code STEP05RSORT} here.
 *
 * <p>Launch contract - the CR00 screen (S-07 Reporting) submitted this job
 * through the CICS internal reader with the report type and the date range
 * substituted into the JCL (app/cbl/CORPT00C.cbl:449-470). The migrated
 * equivalent is three job parameters:
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.batch.job.name=TRANREPT \
 *      --reportType=Monthly --startDate=2022-07-01 --endDate=2022-07-06
 * </pre>
 *
 * <p>or, in process, {@link TransactionReportLauncher}. The path of the report a
 * run produced is published in the job execution context under
 * {@code tranreport.reportFile}.
 */
@Configuration
public class TranReportJobConfiguration {

    public static final String JOB_NAME = "TRANREPT";

    /** WS-REPORT-NAME on the CR00 screen: {@code Monthly}, {@code Yearly} or {@code Custom}. */
    public static final String PARAM_REPORT_TYPE = "reportType";
    /** PARM-START-DATE, {@code YYYY-MM-DD}. */
    public static final String PARAM_START_DATE = "startDate";
    /** PARM-END-DATE, {@code YYYY-MM-DD}. */
    public static final String PARAM_END_DATE = "endDate";

    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final CardXrefRepository cardXrefs;
    private final TransactionTypeRepository transactionTypes;
    private final TransactionCategoryRepository transactionCategories;
    private final AbendService abendService;
    private final Path outputDir;

    public TranReportJobConfiguration(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                      EntityManagerFactory entityManagerFactory,
                                      CardXrefRepository cardXrefs,
                                      TransactionTypeRepository transactionTypes,
                                      TransactionCategoryRepository transactionCategories,
                                      AbendService abendService,
                                      @Value("${carddemo.batch.output-dir:${java.io.tmpdir}/carddemo-batch}")
                                      String outputDir) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.entityManagerFactory = entityManagerFactory;
        this.cardXrefs = cardXrefs;
        this.transactionTypes = transactionTypes;
        this.transactionCategories = transactionCategories;
        this.abendService = abendService;
        this.outputDir = Paths.get(outputDir);
    }

    @Bean
    public Job tranReportJob(@Qualifier("transactionUnloadStep") Step transactionUnloadStep,
                             @Qualifier("transactionSortStep") Step transactionSortStep,
                             @Qualifier("transactionReportStep") Step transactionReportStep) {
        DefaultJobParametersValidator validator = new DefaultJobParametersValidator(
                new String[]{PARAM_REPORT_TYPE, PARAM_START_DATE, PARAM_END_DATE},
                new String[]{});
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(validator)
                .listener(new TranReportFiles(outputDir))
                .start(transactionUnloadStep)
                .next(transactionSortStep)
                .next(transactionReportStep)
                .build();
    }

    /**
     * STEP05R - {@code EXEC PROC=REPROC} (app/jcl/TRANREPT.jcl:22-32): IDCAMS
     * REPRO of TRANSACT.VSAM.KSDS to a flat 350-byte backup, in key order.
     */
    @Bean
    public Step transactionUnloadStep(JpaPagingItemReader<TransactionRecord> transactionUnloadReader,
                                      FlatFileItemWriter<String> transactionUnloadWriter) {
        return new StepBuilder("STEP05R", jobRepository)
                .<TransactionRecord, String>chunk(CHUNK_SIZE, transactionManager)
                .reader(transactionUnloadReader)
                .processor((ItemProcessor<TransactionRecord, String>) TransactionRecordImage::of)
                .writer(transactionUnloadWriter)
                .build();
    }

    @Bean
    @StepScope
    public JpaPagingItemReader<TransactionRecord> transactionUnloadReader() {
        return new JpaPagingItemReaderBuilder<TransactionRecord>()
                .name("transactionUnloadReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("select t from TransactionRecord t order by t.id")
                .pageSize(CHUNK_SIZE)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemWriter<String> transactionUnloadWriter(
            @Value("#{jobExecutionContext['" + TranReportFiles.BACKUP_FILE + "']}") String backupFile) {
        return new FlatFileItemWriterBuilder<String>()
                .name("transactionUnloadWriter")
                .resource(new FileSystemResource(backupFile))
                .lineAggregator(new PassThroughLineAggregator<>())
                .build();
    }

    /**
     * STEP05R (SORT) - app/jcl/TRANREPT.jcl:36-56. A DFSORT run is one utility
     * step with no restart point of its own, so it is a tasklet.
     */
    @Bean
    public Step transactionSortStep(Tasklet transactionSortTasklet) {
        return new StepBuilder("STEP05RSORT", jobRepository)
                .tasklet(transactionSortTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet transactionSortTasklet(
            @Value("#{jobExecutionContext['" + TranReportFiles.BACKUP_FILE + "']}") String backupFile,
            @Value("#{jobExecutionContext['" + TranReportFiles.EXTRACT_FILE + "']}") String extractFile,
            @Value("#{jobParameters['" + PARAM_START_DATE + "']}") String startDate,
            @Value("#{jobParameters['" + PARAM_END_DATE + "']}") String endDate) {
        return new TransactionExtractSortTasklet(
                Paths.get(backupFile), Paths.get(extractFile), startDate, endDate);
    }

    /** STEP10R - {@code EXEC PGM=CBTRN03C} (app/jcl/TRANREPT.jcl:58-80). */
    @Bean
    public Step transactionReportStep(FlatFileItemReader<String> transactionExtractReader,
                                      TransactionReportWriter transactionReportWriter) {
        return new StepBuilder("STEP10R", jobRepository)
                .<String, PostedTransaction>chunk(CHUNK_SIZE, transactionManager)
                .reader(transactionExtractReader)
                .processor((ItemProcessor<String, PostedTransaction>) TransactionRecordImage::parse)
                .writer(transactionReportWriter)
                .listener(transactionReportWriter)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<String> transactionExtractReader(
            @Value("#{jobExecutionContext['" + TranReportFiles.EXTRACT_FILE + "']}") String extractFile) {
        return new FlatFileItemReaderBuilder<String>()
                .name("transactionExtractReader")
                .resource(new FileSystemResource(extractFile))
                .lineMapper(new PassThroughLineMapper())
                .build();
    }

    @Bean
    @StepScope
    public TransactionReportWriter transactionReportWriter(
            @Value("#{jobExecutionContext['" + TranReportFiles.REPORT_FILE + "']}") String reportFile,
            @Value("#{jobParameters['" + PARAM_START_DATE + "']}") String startDate,
            @Value("#{jobParameters['" + PARAM_END_DATE + "']}") String endDate) {
        return new TransactionReportWriter(Paths.get(reportFile), startDate, endDate,
                cardXrefs, transactionTypes, transactionCategories, abendService);
    }
}
