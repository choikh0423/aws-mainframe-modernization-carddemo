package com.carddemo.batch.config;

import com.carddemo.batch.model.CardXref;
import com.carddemo.batch.repository.CardXrefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

/**
 * Spring Batch job configuration for READXREF.
 * Migrated from CBACT03C.cbl and READXREF.jcl.
 *
 * Reads all CardXref entities sequentially and displays/prints them.
 */
@Configuration
public class ReadXrefJobConfig {

    private static final Logger log = LoggerFactory.getLogger(ReadXrefJobConfig.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CardXrefRepository cardXrefRepository;

    public ReadXrefJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            CardXrefRepository cardXrefRepository) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.cardXrefRepository = cardXrefRepository;
    }

    @Bean
    public Job readXrefJob() {
        return new JobBuilder("readXrefJob", jobRepository)
                .start(readXrefStep())
                .build();
    }

    @Bean
    public Step readXrefStep() {
        return new StepBuilder("readXrefStep", jobRepository)
                .tasklet((StepContribution contribution, ChunkContext chunkContext) -> {
                    log.info("START OF EXECUTION OF PROGRAM READXREF (CBACT03C)");

                    List<CardXref> xrefs = cardXrefRepository.findAll();
                    for (CardXref xref : xrefs) {
                        log.info("CARD-NUM: {} CUST-ID: {} ACCT-ID: {}",
                                xref.getCardNum(), xref.getCustId(), xref.getAcctId());
                    }

                    log.info("END OF EXECUTION OF PROGRAM READXREF - {} records", xrefs.size());
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
