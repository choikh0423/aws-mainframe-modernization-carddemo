package com.carddemo.batch.filereads;

import com.carddemo.common.domain.AccountRecord;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Job READACCT - prints the account master file and derives three sequential datasets
 * from it (`app/jcl/READACCT.jcl` → `app/cbl/CBACT01C.cbl`).
 *
 * <p>Step PREDEL is the {@code IEFBR14} step that deletes the three output datasets if
 * a previous run catalogued them (`READACCT.jcl:22-28`); step STEP05 runs the program.
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.batch.job.name=READACCT
 * </pre>
 */
@Configuration
public class ReadAcctJobConfiguration {

    static final String JOB_NAME = "READACCT";
    static final String PREDELETE_STEP = "PREDEL";
    static final String PRINT_STEP = "STEP05";

    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final FileReadOutputs outputs;
    private final AccountExtractWriter extracts;
    private final FileReadAbend abendSeam;

    public ReadAcctJobConfiguration(JobRepository jobRepository,
                                    PlatformTransactionManager transactionManager,
                                    EntityManagerFactory entityManagerFactory,
                                    FileReadOutputs outputs,
                                    AccountExtractWriter extracts,
                                    FileReadAbend abendSeam) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.entityManagerFactory = entityManagerFactory;
        this.outputs = outputs;
        this.extracts = extracts;
        this.abendSeam = abendSeam;
    }

    @Bean
    public Job readAcctJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(predeleteStep())
                .next(printStep())
                .build();
    }

    /** PREDEL - IEFBR14 with DISP=(MOD,DELETE,DELETE) on the three output datasets. */
    private Step predeleteStep() {
        Tasklet tasklet = (contribution, chunkContext) -> {
            List<Path> datasets = List.of(outputs.accountExtract(), outputs.accountArray(),
                    outputs.accountVariable());
            for (Path dataset : datasets) {
                try {
                    Files.deleteIfExists(dataset);
                } catch (IOException e) {
                    throw new UncheckedIOException("Cannot delete " + dataset, e);
                }
            }
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder(PREDELETE_STEP, jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    /** STEP05 - EXEC PGM=CBACT01C. */
    private Step printStep() {
        AccountPrintWriter writer = new AccountPrintWriter(
                outputs.sysout(JOB_NAME), extracts, abendSeam);
        ItemStreamReader<AccountRecord> reader = new AbendingItemReader<>(
                new JpaPagingItemReaderBuilder<AccountRecord>()
                        .name(PRINT_STEP + "Reader")
                        .entityManagerFactory(entityManagerFactory)
                        .queryString("select a from AccountRecord a order by a.acctId")
                        .pageSize(CHUNK_SIZE)
                        .build(),
                writer,
                AccountPrintWriter.OPEN_ACCTFILE_ERROR,
                AccountPrintWriter.READ_ERROR,
                AccountPrintWriter.CLOSE_ERROR);
        return new StepBuilder(PRINT_STEP, jobRepository)
                .<AccountRecord, AccountRecord>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .writer(writer)
                .build();
    }
}
