package com.carddemo.batch.config;

import com.carddemo.batch.model.Card;
import com.carddemo.batch.repository.CardRepository;
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
 * Spring Batch job configuration for READCARD.
 * Migrated from CBACT02C.cbl and READCARD.jcl.
 *
 * Reads all Card entities sequentially and displays/prints them.
 */
@Configuration
public class ReadCardJobConfig {

    private static final Logger log = LoggerFactory.getLogger(ReadCardJobConfig.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CardRepository cardRepository;

    public ReadCardJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            CardRepository cardRepository) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.cardRepository = cardRepository;
    }

    @Bean
    public Job readCardJob() {
        return new JobBuilder("readCardJob", jobRepository)
                .start(readCardStep())
                .build();
    }

    @Bean
    public Step readCardStep() {
        return new StepBuilder("readCardStep", jobRepository)
                .tasklet((StepContribution contribution, ChunkContext chunkContext) -> {
                    log.info("START OF EXECUTION OF PROGRAM READCARD (CBACT02C)");

                    List<Card> cards = cardRepository.findAll();
                    for (Card card : cards) {
                        log.info("CARD-NUM: {} ACCT-ID: {} CVV: {} NAME: {} EXP: {} STATUS: {}",
                                card.getCardNum(), card.getCardAcctId(), card.getCardCvvCd(),
                                card.getCardEmbossedName(), card.getCardExpirationDate(),
                                card.getCardActiveStatus());
                    }

                    log.info("END OF EXECUTION OF PROGRAM READCARD - {} records", cards.size());
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
