package com.carddemo.batch.filereads;

import com.carddemo.common.domain.CustomerRecord;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Job READCUST - prints the customer master file (`app/jcl/READCUST.jcl` →
 * `app/cbl/CBCUS01C.cbl`).
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.batch.job.name=READCUST
 * </pre>
 */
@Configuration
public class ReadCustJobConfiguration {

    static final String JOB_NAME = "READCUST";
    static final String STEP_NAME = "STEP05";
    static final String PROGRAM = "CBCUS01C";

    static final String OPEN_ERROR = "ERROR OPENING CUSTFILE";
    static final String READ_ERROR = "ERROR READING CUSTOMER FILE";
    static final String CLOSE_ERROR = "ERROR CLOSING CUSTOMER FILE";

    /** CBCUS01C displays each record twice: in the read paragraph and in the main loop. */
    private static final int COPIES = 2;

    private final JobRepository jobRepository;
    private final PrintSteps printSteps;

    public ReadCustJobConfiguration(JobRepository jobRepository, PrintSteps printSteps) {
        this.jobRepository = jobRepository;
        this.printSteps = printSteps;
    }

    @Bean
    public Job readCustJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(printSteps.<CustomerRecord>printStep(JOB_NAME, STEP_NAME, PROGRAM,
                        "select c from CustomerRecord c order by c.custId",
                        RecordImages::customer, COPIES,
                        OPEN_ERROR, READ_ERROR, CLOSE_ERROR))
                .build();
    }
}
