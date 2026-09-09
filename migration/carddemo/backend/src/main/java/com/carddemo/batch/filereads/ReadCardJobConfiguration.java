package com.carddemo.batch.filereads;

import com.carddemo.common.domain.CardRecord;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Job READCARD - prints the card master file (`app/jcl/READCARD.jcl` →
 * `app/cbl/CBACT02C.cbl`).
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.batch.job.name=READCARD
 * </pre>
 */
@Configuration
public class ReadCardJobConfiguration {

    static final String JOB_NAME = "READCARD";
    static final String STEP_NAME = "STEP05";
    static final String PROGRAM = "CBACT02C";

    static final String OPEN_ERROR = "ERROR OPENING CARDFILE";
    static final String READ_ERROR = "ERROR READING CARDFILE";
    static final String CLOSE_ERROR = "ERROR CLOSING CARDFILE";

    /** CBACT02C prints each card once: the read paragraph's DISPLAY is commented out. */
    private static final int COPIES = 1;

    private final JobRepository jobRepository;
    private final PrintSteps printSteps;

    public ReadCardJobConfiguration(JobRepository jobRepository, PrintSteps printSteps) {
        this.jobRepository = jobRepository;
        this.printSteps = printSteps;
    }

    @Bean
    public Job readCardJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(printSteps.<CardRecord>printStep(JOB_NAME, STEP_NAME, PROGRAM,
                        "select c from CardRecord c order by c.cardNum",
                        RecordImages::card, COPIES,
                        OPEN_ERROR, READ_ERROR, CLOSE_ERROR))
                .build();
    }
}
