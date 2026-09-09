package com.carddemo.batch.intcalc;

import com.carddemo.common.domain.TransactionRecord;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.JobParametersValidator;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * COMBTRAN — {@code app/jcl/COMBTRAN.jcl}: combine the system transactions INTCALC
 * generated with the daily ones already on the master, then load the result.
 *
 * <pre>
 * //STEP05R  EXEC PGM=SORT     SORTIN = TRANSACT.BKUP(0) + SYSTRAN(0)
 * //                           SORT FIELDS=(TRAN-ID,A)   -&gt; TRANSACT.COMBINED(+1)
 * //STEP10   EXEC PGM=IDCAMS   REPRO INFILE(TRANSACT) OUTFILE(TRANVSAM)
 * </pre>
 *
 * <p>One step per {@code EXEC PGM=} (D-3), named after the JCL steps. The utilities
 * have no COBOL, so the control cards are the specification: a character-collating
 * ascending sort on bytes 1-16 with no {@code SUM FIELDS}, and an unconditional
 * REPRO into the transaction master.
 *
 * <pre>
 * java -jar carddemo.jar --spring.batch.job.name=COMBTRAN run.date=2022071800
 * </pre>
 */
@Configuration
public class CombineTransactionsJobConfiguration {

    private static final int CHUNK_SIZE = 200;

    private static final int RUN_DATE_LENGTH = 10;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final String workDir;

    public CombineTransactionsJobConfiguration(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            EntityManagerFactory entityManagerFactory,
            @Value("${carddemo.batch.intcalc.work-dir}") String workDir) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.entityManagerFactory = entityManagerFactory;
        this.workDir = workDir;
    }

    @Bean
    public Job combineTransactionsJob(Step combineTransactionsSortStep,
                                      Step combineTransactionsReproStep) {
        return new JobBuilder("COMBTRAN", jobRepository)
                .validator(runDateValidator())
                .start(combineTransactionsSortStep)
                .next(combineTransactionsReproStep)
                .build();
    }

    private JobParametersValidator runDateValidator() {
        return new JobParametersValidator() {
            @Override
            public void validate(JobParameters parameters) throws JobParametersInvalidException {
                String runDate = parameters == null ? null : parameters.getString("run.date");
                if (runDate == null || runDate.length() != RUN_DATE_LENGTH) {
                    throw new JobParametersInvalidException(
                            "COMBTRAN requires run.date, the generation of the SYSTRAN file"
                                    + " written by INTCALC, as exactly "
                                    + RUN_DATE_LENGTH + " characters");
                }
            }
        };
    }

    /** STEP05R: DFSORT over the SORTIN concatenation. */
    @Bean
    public Step combineTransactionsSortStep(CombineTransactionsTasklet combineTransactionsTasklet) {
        return new StepBuilder("STEP05R", jobRepository)
                .tasklet(combineTransactionsTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public CombineTransactionsTasklet combineTransactionsTasklet(
            com.carddemo.common.repository.TransactionRepository transactions,
            @Value("#{jobParameters['run.date']}") String runDate,
            @Value("#{jobParameters['systran.file']}") String systranFile,
            @Value("#{jobParameters['combined.file']}") String combinedFile) {
        return new CombineTransactionsTasklet(
                transactions,
                systranFile != null ? systranFile : SystranFiles.systran(workDir, runDate),
                combinedFile != null ? combinedFile : SystranFiles.combined(workDir, runDate));
    }

    /** STEP10: IDCAMS REPRO of the combined generation into the transaction master. */
    @Bean
    public Step combineTransactionsReproStep(FlatFileItemReader<String> combinedTransactionReader,
                                             JpaItemWriter<TransactionRecord> transactionMasterWriter) {
        return new StepBuilder("STEP10", jobRepository)
                .<String, TransactionRecord>chunk(CHUNK_SIZE, transactionManager)
                .reader(combinedTransactionReader)
                .processor((ItemProcessor<String, TransactionRecord>) TransactionRecordLine::parse)
                .writer(transactionMasterWriter)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<String> combinedTransactionReader(
            @Value("#{jobParameters['run.date']}") String runDate,
            @Value("#{jobParameters['combined.file']}") String combinedFile) {
        String source = combinedFile != null ? combinedFile : SystranFiles.combined(workDir, runDate);
        return new FlatFileItemReaderBuilder<String>()
                .name("combinedTransactionReader")
                .resource(new FileSystemResource(source))
                .lineMapper((line, lineNumber) -> line)
                .build();
    }

    @Bean
    public JpaItemWriter<TransactionRecord> transactionMasterWriter() {
        return new JpaItemWriterBuilder<TransactionRecord>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }
}
