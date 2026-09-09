package com.carddemo.batch.exportimport;

import com.carddemo.common.batch.AbendService;
import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CardRecord;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.CustomerRecord;
import com.carddemo.common.domain.TransactionRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.CardRepository;
import com.carddemo.common.repository.CardXrefRepository;
import com.carddemo.common.repository.CustomerRepository;
import com.carddemo.common.repository.TransactionRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Job CBEXPORT - {@code app/jcl/CBEXPORT.jcl}, program {@code app/cbl/CBEXPORT.cbl}.
 *
 * <p>One step per {@code EXEC PGM=}:
 * <ul>
 *   <li>STEP01, the IDCAMS {@code DELETE}/{@code DEFINE} of the export cluster:
 *       there is no VSAM cluster to define against a directory hand-off, so what
 *       survives of the step is its actual effect - the export file starts each
 *       run empty (FR-E-02, FR-E-03). It also captures the run timestamp every
 *       record is stamped with, matching 1050-GENERATE-TIMESTAMP.</li>
 *   <li>STEP02, the export pass: the five master files are read in the program's
 *       order and written to the export file as 500-byte records.</li>
 * </ul>
 *
 * <p>The master data lives in the shared tables rather than in VSAM KSDS files,
 * so the readers are paging repository readers sorted on the legacy key of each
 * file, which is what {@code ACCESS MODE IS SEQUENTIAL} gave the COBOL program.
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.profiles.active=postgres --spring.batch.job.name=CBEXPORT
 * </pre>
 */
@Configuration
public class ExportJobConfiguration {

    /** The run timestamp, shared by STEP01 (which sets it) and STEP02. */
    static final String TIMESTAMP_KEY = "carddemo.exportimport.export.timestamp";
    /** The job log CBEXPORT would have produced, for operators and for tests. */
    public static final String DISPLAY_LINES_KEY = "carddemo.exportimport.export.display";

    /** AWS.M2.CARDDEMO.EXPORT.DATA, the file both jobs hand over through. */
    public static final String EXPORT_FILE_NAME = "export.data";

    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final CardXrefRepository cardXrefRepository;
    private final TransactionRepository transactionRepository;
    private final CardRepository cardRepository;
    private final AbendService abendService;
    private final Clock clock;
    private final Path directory;

    public ExportJobConfiguration(JobRepository jobRepository,
                                  PlatformTransactionManager transactionManager,
                                  CustomerRepository customerRepository,
                                  AccountRepository accountRepository,
                                  CardXrefRepository cardXrefRepository,
                                  TransactionRepository transactionRepository,
                                  CardRepository cardRepository,
                                  AbendService abendService,
                                  ObjectProvider<Clock> clock,
                                  @Value("${carddemo.batch.export-import-dir}") String directory) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.cardXrefRepository = cardXrefRepository;
        this.transactionRepository = transactionRepository;
        this.cardRepository = cardRepository;
        this.abendService = abendService;
        this.clock = clock.getIfAvailable(Clock::systemDefaultZone);
        this.directory = Path.of(directory);
    }

    @Bean
    public Job exportJob(@Qualifier("exportDefineStep") Step exportDefineStep,
                         @Qualifier("exportStep") Step exportStep) {
        return new JobBuilder("CBEXPORT", jobRepository)
                .start(exportDefineStep)
                .next(exportStep)
                .build();
    }

    /** The counters and DISPLAY lines of one CBEXPORT run. */
    @Bean
    @JobScope
    public ExportStatistics exportStatistics() {
        return new ExportStatistics();
    }

    /** STEP01 - IDCAMS DELETE + DEFINE of the export file (CBEXPORT.jcl:26-40). */
    @Bean
    public Step exportDefineStep(ExportStatistics statistics) {
        Tasklet tasklet = (contribution, chunkContext) -> {
            OffsetDateTime now = OffsetDateTime.now(clock);
            Files.createDirectories(directory);
            Files.deleteIfExists(directory.resolve(EXPORT_FILE_NAME));
            Files.createFile(directory.resolve(EXPORT_FILE_NAME));
            chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                    .putString(TIMESTAMP_KEY, MainframeTimestamps.exportTimestamp(now));
            statistics.starting(MainframeTimestamps.exportDate(now), MainframeTimestamps.exportTime(now));
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder("STEP01", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    /** STEP02 - the CBEXPORT program itself. */
    @Bean
    public Step exportStep(ExportSourceItemReader reader,
                           ItemProcessor<Object, byte[]> exportRecordProcessor,
                           ExportFileItemWriter writer,
                           ExportStatistics statistics) {
        return new StepBuilder("STEP02", jobRepository)
                .<Object, byte[]>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .processor(exportRecordProcessor)
                .writer(writer)
                .listener(new StepExecutionListener() {
                    @Override
                    public org.springframework.batch.core.ExitStatus afterStep(StepExecution stepExecution) {
                        statistics.completed();
                        stepExecution.getJobExecution().getExecutionContext()
                                .putString(DISPLAY_LINES_KEY, String.join("\n", statistics.lines()));
                        return stepExecution.getExitStatus();
                    }
                })
                .build();
    }

    @Bean
    @StepScope
    public ExportSourceItemReader exportSourceItemReader(ExportStatistics statistics) {
        return new ExportSourceItemReader(List.of(
                new ExportSourceItemReader.Group(ExportRecordCodec.TYPE_CUSTOMER,
                        keyOrderedReader("CUSTFILE", customerRepository, "custId")),
                new ExportSourceItemReader.Group(ExportRecordCodec.TYPE_ACCOUNT,
                        keyOrderedReader("ACCTFILE", accountRepository, "acctId")),
                new ExportSourceItemReader.Group(ExportRecordCodec.TYPE_XREF,
                        keyOrderedReader("XREFFILE", cardXrefRepository, "cardNum")),
                new ExportSourceItemReader.Group(ExportRecordCodec.TYPE_TRANSACTION,
                        keyOrderedReader("TRANSACT", transactionRepository, "id")),
                new ExportSourceItemReader.Group(ExportRecordCodec.TYPE_CARD,
                        keyOrderedReader("CARDFILE", cardRepository, "cardNum"))),
                statistics);
    }

    @Bean
    @StepScope
    public ItemProcessor<Object, byte[]> exportRecordProcessor(
            @Value("#{jobExecutionContext['" + TIMESTAMP_KEY + "']}") String timestamp) {
        return item -> {
            if (item instanceof CustomerRecord customer) {
                return ExportRecordCodec.customerRecord(customer, timestamp);
            }
            if (item instanceof AccountRecord account) {
                return ExportRecordCodec.accountRecord(account, timestamp);
            }
            if (item instanceof CardXrefRecord xref) {
                return ExportRecordCodec.cardXrefRecord(xref, timestamp);
            }
            if (item instanceof TransactionRecord transaction) {
                return ExportRecordCodec.transactionRecord(transaction, timestamp);
            }
            if (item instanceof CardRecord card) {
                return ExportRecordCodec.cardRecord(card, timestamp);
            }
            throw new IllegalStateException("No export record type for " + item.getClass().getName());
        };
    }

    @Bean
    @StepScope
    public ExportFileItemWriter exportFileItemWriter() throws IOException {
        Files.createDirectories(directory);
        return new ExportFileItemWriter(directory.resolve(EXPORT_FILE_NAME), abendService);
    }

    /**
     * A reader over one master table in ascending legacy-key order - the target's
     * equivalent of reading a KSDS with ACCESS MODE IS SEQUENTIAL.
     */
    private <T> ItemStreamReader<T> keyOrderedReader(String ddName,
                                                     PagingAndSortingRepository<T, ?> repository,
                                                     String keyProperty) {
        return new RepositoryItemReaderBuilder<T>()
                .name(ddName + "Reader")
                .repository(repository)
                .methodName("findAll")
                .sorts(Map.of(keyProperty, Sort.Direction.ASC))
                .pageSize(CHUNK_SIZE)
                .build();
    }
}
