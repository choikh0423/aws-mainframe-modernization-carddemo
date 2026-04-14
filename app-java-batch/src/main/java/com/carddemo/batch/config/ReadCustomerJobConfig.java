package com.carddemo.batch.config;

import com.carddemo.batch.model.Customer;
import com.carddemo.batch.repository.CustomerRepository;
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
 * Spring Batch job configuration for READCUST.
 * Migrated from CBCUS01C.cbl and READCUST.jcl.
 *
 * Reads all Customer entities sequentially and displays/prints them.
 */
@Configuration
public class ReadCustomerJobConfig {

    private static final Logger log = LoggerFactory.getLogger(ReadCustomerJobConfig.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CustomerRepository customerRepository;

    public ReadCustomerJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            CustomerRepository customerRepository) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.customerRepository = customerRepository;
    }

    @Bean
    public Job readCustomerJob() {
        return new JobBuilder("readCustomerJob", jobRepository)
                .start(readCustomerStep())
                .build();
    }

    @Bean
    public Step readCustomerStep() {
        return new StepBuilder("readCustomerStep", jobRepository)
                .tasklet((StepContribution contribution, ChunkContext chunkContext) -> {
                    log.info("START OF EXECUTION OF PROGRAM READCUST (CBCUS01C)");

                    List<Customer> customers = customerRepository.findAll();
                    for (Customer cust : customers) {
                        log.info("CUST-ID: {} NAME: {} {} {} STATE: {} ZIP: {}",
                                cust.getCustId(), cust.getFirstName(),
                                cust.getMiddleName(), cust.getLastName(),
                                cust.getStateCd(), cust.getZip());
                    }

                    log.info("END OF EXECUTION OF PROGRAM READCUST - {} records", customers.size());
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
