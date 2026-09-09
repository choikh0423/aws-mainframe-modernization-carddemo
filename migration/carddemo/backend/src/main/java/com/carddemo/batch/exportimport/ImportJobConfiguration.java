package com.carddemo.batch.exportimport;

import com.carddemo.common.batch.AbendService;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Job CBIMPORT - {@code app/jcl/CBIMPORT.jcl}, program {@code app/cbl/CBIMPORT.cbl}.
 *
 * <p>One {@code EXEC PGM=}, so one step: read the export file, dispatch each
 * record on its type byte, and write it to the normalised file for that type or,
 * for an unrecognised type, to the error file.
 *
 * <p>The job produces files, exactly as the legacy program did; loading them
 * into the database is the DATALOAD job's business (S-18), which is why nothing
 * here touches a repository (FR-S-11).
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.profiles.active=postgres --spring.batch.job.name=CBIMPORT
 * </pre>
 */
@Configuration
public class ImportJobConfiguration {

    /** The job log CBIMPORT would have produced, for operators and for tests. */
    public static final String DISPLAY_LINES_KEY = "carddemo.exportimport.import.display";

    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final AbendService abendService;
    private final Clock clock;
    private final Path directory;

    public ImportJobConfiguration(JobRepository jobRepository,
                                  PlatformTransactionManager transactionManager,
                                  AbendService abendService,
                                  ObjectProvider<Clock> clock,
                                  @Value("${carddemo.batch.export-import-dir}") String directory) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.abendService = abendService;
        this.clock = clock.getIfAvailable(Clock::systemDefaultZone);
        this.directory = Path.of(directory);
    }

    @Bean
    public Job importJob(@Qualifier("importStep") Step importStep) {
        return new JobBuilder("CBIMPORT", jobRepository)
                .start(importStep)
                .build();
    }

    /** The counters and DISPLAY lines of one CBIMPORT run. */
    @Bean
    @JobScope
    public ImportStatistics importStatistics() {
        return new ImportStatistics();
    }

    /** STEP01 - the CBIMPORT program itself. */
    @Bean
    public Step importStep(ExportFileItemReader reader,
                           ItemProcessor<byte[], ImportedRecord> importRecordProcessor,
                           ImportOutputItemWriter writer,
                           ImportStatistics statistics) {
        return new StepBuilder("STEP01", jobRepository)
                .<byte[], ImportedRecord>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .processor(importRecordProcessor)
                .writer(writer)
                .listener(new StepExecutionListener() {
                    @Override
                    public void beforeStep(StepExecution stepExecution) {
                        OffsetDateTime now = OffsetDateTime.now(clock);
                        statistics.starting(MainframeTimestamps.exportDate(now),
                                MainframeTimestamps.exportTime(now));
                    }

                    @Override
                    public ExitStatus afterStep(StepExecution stepExecution) {
                        statistics.validationCompleted();
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
    public ExportFileItemReader exportFileItemReader(ImportStatistics statistics) {
        return new ExportFileItemReader(directory.resolve(ExportJobConfiguration.EXPORT_FILE_NAME),
                abendService, statistics);
    }

    @Bean
    @StepScope
    public ItemProcessor<byte[], ImportedRecord> importRecordProcessor(ImportStatistics statistics) {
        return record -> {
            ImportedRecord imported = ImportRecordCodec.decode(record);
            if (imported != null) {
                return imported;
            }
            statistics.unknownRecordType();
            return new ImportedRecord(ImportTarget.ERROR,
                    ImportErrorRecord.unknownRecordType(record, OffsetDateTime.now(clock)));
        };
    }

    @Bean
    @StepScope
    public ImportOutputItemWriter importOutputItemWriter(ImportStatistics statistics) throws IOException {
        Files.createDirectories(directory);
        return new ImportOutputItemWriter(directory, abendService, statistics);
    }
}
