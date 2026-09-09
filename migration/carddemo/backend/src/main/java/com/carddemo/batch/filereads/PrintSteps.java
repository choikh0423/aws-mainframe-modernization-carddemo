package com.carddemo.batch.filereads;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.function.Function;

/**
 * Builds the single print step of READCARD, READXREF and READCUST, which differ only in
 * the file they browse, the program name they print and how many times each record is
 * displayed.
 *
 * <p>The step is chunk oriented so a restart resumes at the last committed chunk from
 * the JobRepository (D-3); the browse is a key-ordered paging query, the sequential
 * VSAM read of the COBOL (FR-G3).
 */
@Component
public class PrintSteps {

    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final FileReadOutputs outputs;
    private final FileReadAbend abendSeam;

    public PrintSteps(JobRepository jobRepository,
                      PlatformTransactionManager transactionManager,
                      EntityManagerFactory entityManagerFactory,
                      FileReadOutputs outputs,
                      FileReadAbend abendSeam) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.entityManagerFactory = entityManagerFactory;
        this.outputs = outputs;
        this.abendSeam = abendSeam;
    }

    /**
     * @param jobName    the JCL job the step belongs to, which names its SYSOUT
     * @param stepName   the JCL step name
     * @param program    the COBOL program reproduced by the step
     * @param query      the key-ordered browse of the input file
     * @param image      renders one row as the record image the program displayed
     * @param copies     how many times the program displayed each record
     */
    public <T> Step printStep(String jobName, String stepName, String program,
                              String query, Function<T, String> image, int copies,
                              String openError, String readError, String closeError) {
        RecordPrintWriter<T> writer = new RecordPrintWriter<>(
                program, outputs.sysout(jobName), image, copies, abendSeam);
        ItemStreamReader<T> reader = new AbendingItemReader<>(
                new JpaPagingItemReaderBuilder<T>()
                        .name(stepName + "Reader")
                        .entityManagerFactory(entityManagerFactory)
                        .queryString(query)
                        .pageSize(CHUNK_SIZE)
                        .build(),
                writer, openError, readError, closeError);
        return new StepBuilder(stepName, jobRepository)
                .<T, T>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .writer(writer)
                .build();
    }
}
