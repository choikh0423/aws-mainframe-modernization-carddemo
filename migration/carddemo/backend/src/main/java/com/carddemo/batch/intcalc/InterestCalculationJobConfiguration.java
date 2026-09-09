package com.carddemo.batch.intcalc;

import com.carddemo.common.batch.AbendService;
import com.carddemo.common.domain.TransactionCategoryBalanceRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.CardXrefRepository;
import com.carddemo.common.repository.DisclosureGroupRepository;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.JobParametersValidator;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

/**
 * INTCALC — {@code app/jcl/INTCALC.jcl}.
 *
 * <pre>
 * //STEP15   EXEC PGM=CBACT04C,PARM='2022071800'
 * </pre>
 *
 * <p>One job, one step per {@code EXEC PGM=} (D-3). The PARM is the job parameter
 * {@code run.date}: the business date of the run, used to build transaction ids,
 * never read from the clock (FR-I2). The job is launched by name through
 * {@code BatchJobLauncher}; it never runs on start-up.
 *
 * <pre>
 * java -jar carddemo.jar --spring.batch.job.name=INTCALC run.date=2022071800
 * </pre>
 *
 * <p>The COBOL browsed TCATBALF sequentially and kept the current account in
 * WORKING-STORAGE; here the same key-ordered browse is grouped into one item per
 * account ({@link AccountBalanceGroupReader}), so a chunk boundary can only fall
 * between accounts and a restart never re-posts half an account.
 */
@Configuration
public class InterestCalculationJobConfiguration {

    /** Accounts per chunk; each is one account's TCATBALF group. */
    private static final int CHUNK_SIZE = 50;

    private static final int PAGE_SIZE = 200;

    /** PARM-DATE PIC X(10) (CBACT04C.cbl:177). */
    private static final int RUN_DATE_LENGTH = 10;

    private static final Logger log = LoggerFactory.getLogger("CBACT04C");

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final AccountRepository accounts;
    private final CardXrefRepository cardXrefs;
    private final DisclosureGroupRepository disclosureGroups;
    private final AbendService abendService;
    private final String workDir;

    public InterestCalculationJobConfiguration(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            EntityManagerFactory entityManagerFactory,
            AccountRepository accounts,
            CardXrefRepository cardXrefs,
            DisclosureGroupRepository disclosureGroups,
            AbendService abendService,
            @Value("${carddemo.batch.intcalc.work-dir}") String workDir) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.entityManagerFactory = entityManagerFactory;
        this.accounts = accounts;
        this.cardXrefs = cardXrefs;
        this.disclosureGroups = disclosureGroups;
        this.abendService = abendService;
        this.workDir = workDir;
    }

    @Bean
    public Job interestCalculationJob(Step interestCalculationStep) {
        return new JobBuilder("INTCALC", jobRepository)
                .validator(runDateValidator())
                .start(interestCalculationStep)
                .build();
    }

    /** {@code run.date} is the PARM: mandatory, exactly ten characters, not parsed. */
    private JobParametersValidator runDateValidator() {
        return new JobParametersValidator() {
            @Override
            public void validate(JobParameters parameters) throws JobParametersInvalidException {
                String runDate = parameters == null ? null : parameters.getString("run.date");
                if (runDate == null || runDate.length() != RUN_DATE_LENGTH) {
                    throw new JobParametersInvalidException(
                            "INTCALC requires run.date, the PARM of STEP15, as exactly "
                                    + RUN_DATE_LENGTH + " characters");
                }
            }
        };
    }

    @Bean
    public Step interestCalculationStep(AccountBalanceGroupReader transactionCategoryBalanceGroupReader,
                                        InterestCalculationProcessor interestCalculationProcessor,
                                        InterestPostingWriter interestPostingWriter,
                                        TranIdSequence tranIdSequence) {
        return new StepBuilder("STEP15", jobRepository)
                .<AccountBalanceGroup, InterestPosting>chunk(CHUNK_SIZE, transactionManager)
                .reader(transactionCategoryBalanceGroupReader)
                .processor(interestCalculationProcessor)
                .writer(interestPostingWriter)
                .stream(tranIdSequence)
                .listener(programBanners())
                .build();
    }

    /** CBACT04C.cbl:181,230. */
    private StepExecutionListener programBanners() {
        return new StepExecutionListener() {
            @Override
            public void beforeStep(StepExecution stepExecution) {
                log.info("START OF EXECUTION OF PROGRAM CBACT04C");
            }

            @Override
            public org.springframework.batch.core.ExitStatus afterStep(StepExecution stepExecution) {
                log.info("END OF EXECUTION OF PROGRAM CBACT04C");
                return null;
            }
        };
    }

    /** The TCATBALF browse, in KSDS key order (CBACT04C.cbl:28-32). */
    @Bean
    public AccountBalanceGroupReader transactionCategoryBalanceGroupReader() {
        JpaPagingItemReader<TransactionCategoryBalanceRecord> balances =
                new JpaPagingItemReaderBuilder<TransactionCategoryBalanceRecord>()
                        .name("tcatbalfReader")
                        .entityManagerFactory(entityManagerFactory)
                        .queryString("select b from TransactionCategoryBalanceRecord b"
                                + " order by b.id.acctId, b.id.typeCd, b.id.catCd")
                        .pageSize(PAGE_SIZE)
                        .build();
        return new AccountBalanceGroupReader(balances);
    }

    @Bean
    @StepScope
    public TranIdSequence tranIdSequence(@Value("#{jobParameters['run.date']}") String runDate) {
        return new TranIdSequence(runDate);
    }

    @Bean
    @StepScope
    public InterestCalculationProcessor interestCalculationProcessor(TranIdSequence tranIdSequence) {
        return new InterestCalculationProcessor(new InterestCalculator(
                accounts, cardXrefs, disclosureGroups, abendService,
                tranIdSequence, Clock.systemDefaultZone()));
    }

    @Bean
    @StepScope
    public InterestPostingWriter interestPostingWriter(FlatFileItemWriter<String> systranWriter) {
        return new InterestPostingWriter(systranWriter, accounts);
    }

    /** {@code TRANSACT DD ... SYSTRAN(+1)}, RECFM=F LRECL=350. */
    @Bean
    @StepScope
    public FlatFileItemWriter<String> systranWriter(
            @Value("#{jobParameters['run.date']}") String runDate,
            @Value("#{jobParameters['systran.file']}") String systranFile) {
        String target = systranFile != null ? systranFile : SystranFiles.systran(workDir, runDate);
        SystranFiles.createParentDirectory(target);
        return new FlatFileItemWriterBuilder<String>()
                .name("systranWriter")
                .resource(new FileSystemResource(target))
                .lineAggregator(line -> line)
                .build();
    }
}
