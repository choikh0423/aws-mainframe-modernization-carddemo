package com.carddemo.batch.authpurge;

import com.carddemo.common.domain.PendingAuthDetailRecord;
import com.carddemo.common.domain.PendingAuthSummaryRecord;
import com.carddemo.common.repository.PendingAuthDetailRepository;
import com.carddemo.common.repository.PendingAuthSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.PassThroughLineMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Job LOADPADB — {@code EXEC PGM=DFSRRC00,PARM='BMP,PAUDBLOD,PSBPAUTB'}: load
 * the pending-authorization database from the two files UNLDPADB produced.
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.profiles.active=postgres --spring.batch.job.name=LOADPADB \
 *      --infile1=/tmp/pautdb.root.txt --infile2=/tmp/pautdb.child.txt
 * </pre>
 *
 * <p>PAUDBLOD reads {@code INFILE1} to completion inserting roots, then
 * {@code INFILE2} inserting children under the root the record's key names, so
 * the migrated job is two steps in that order. An {@code II} status — the
 * segment is already in the database — is not an error in the source: the
 * program displays it and carries on, so a record whose key already exists is
 * skipped rather than overwritten. Any other bad status abends with return code
 * 16, which here is an exception that fails the step.
 *
 * <p>A child record whose root key is non-numeric is skipped without an insert
 * ({@code IF ROOT-SEG-KEY IS NUMERIC}, PAUDBLOD.CBL:283); a child whose root is
 * absent abends in the source on the {@code GU}, and fails the step here.
 */
@Configuration
public class PendingAuthLoadJobConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PendingAuthLoadJobConfiguration.class);

    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final PendingAuthSummaryRepository summaryRepository;
    private final PendingAuthDetailRepository detailRepository;
    private final String dataDir;

    public PendingAuthLoadJobConfiguration(JobRepository jobRepository,
                                           PlatformTransactionManager transactionManager,
                                           PendingAuthSummaryRepository summaryRepository,
                                           PendingAuthDetailRepository detailRepository,
                                           @Value("${carddemo.batch.data-dir}") String dataDir) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.summaryRepository = summaryRepository;
        this.detailRepository = detailRepository;
        this.dataDir = dataDir;
    }

    @Bean
    public Job pendingAuthLoadJob(@Qualifier("loadRootStep") Step rootStep,
                                  @Qualifier("loadChildStep") Step childStep) {
        return new JobBuilder("LOADPADB", jobRepository)
                .start(rootStep)
                .next(childStep)
                .build();
    }

    /** 2000-READ-ROOT-SEG-FILE + 2100-INSERT-ROOT-SEG. */
    @Bean
    @JobScope
    public Step loadRootStep(@Value("#{jobParameters['infile1']}") String infile1) {
        ItemReader<String> reader = lineReader("INFILE1",
                path(infile1, PendingAuthUnloadJobConfiguration.DEFAULT_ROOT_FILE));
        ItemProcessor<String, PendingAuthSummaryRecord> processor = line -> {
            PendingAuthSummaryRecord root = PendingAuthSegmentFormat.parseRoot(line);
            if (summaryRepository.existsById(root.getPaAcctId())) {
                log.info("ROOT SEGMENT ALREADY IN DB");
                return null;
            }
            return root;
        };
        ItemWriter<PendingAuthSummaryRecord> writer = items ->
                summaryRepository.saveAll(items.getItems());
        return new StepBuilder("INFILE1", jobRepository)
                .<String, PendingAuthSummaryRecord>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    /** 3000-READ-CHILD-SEG-FILE + 3100/3200-INSERT-CHILD-SEG. */
    @Bean
    @JobScope
    public Step loadChildStep(@Value("#{jobParameters['infile2']}") String infile2) {
        ItemReader<String> reader = lineReader("INFILE2",
                path(infile2, PendingAuthUnloadJobConfiguration.DEFAULT_CHILD_FILE));
        ItemProcessor<String, PendingAuthDetailRecord> processor = line -> {
            Long rootKey = PendingAuthSegmentFormat.childRootKey(line);
            if (rootKey == null) {
                return null;
            }
            // GU PAUTSUM0 with the qualified SSA: the parent must exist.
            PendingAuthSummaryRecord root = summaryRepository.findById(rootKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "ROOT GU CALL FAIL for account " + rootKey));
            PendingAuthDetailRecord child = PendingAuthSegmentFormat.parseChild(line);
            child.getId().setPaAcctId(root.getPaAcctId());
            if (detailRepository.existsById(child.getId())) {
                log.info("CHILD SEGMENT ALREADY IN DB");
                return null;
            }
            return child;
        };
        ItemWriter<PendingAuthDetailRecord> writer = items ->
                detailRepository.saveAll(items.getItems());
        return new StepBuilder("INFILE2", jobRepository)
                .<String, PendingAuthDetailRecord>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    private ItemReader<String> lineReader(String name, String path) {
        return new FlatFileItemReaderBuilder<String>()
                .name(name + "Reader")
                .resource(new FileSystemResource(path))
                .lineMapper(new PassThroughLineMapper())
                .build();
    }

    private String path(String parameter, String defaultName) {
        return parameter == null || parameter.isBlank()
                ? dataDir + "/" + defaultName : parameter;
    }
}
