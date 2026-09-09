package com.carddemo.batch.filereads;

import com.carddemo.common.domain.CardXrefRecord;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Job READXREF - prints the card cross-reference file (`app/jcl/READXREF.jcl` →
 * `app/cbl/CBACT03C.cbl`).
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.batch.job.name=READXREF
 * </pre>
 */
@Configuration
public class ReadXrefJobConfiguration {

    static final String JOB_NAME = "READXREF";
    static final String STEP_NAME = "STEP05";
    static final String PROGRAM = "CBACT03C";

    static final String OPEN_ERROR = "ERROR OPENING XREFFILE";
    static final String READ_ERROR = "ERROR READING XREFFILE";
    static final String CLOSE_ERROR = "ERROR CLOSING XREFFILE";

    /** CBACT03C displays each record twice: in the read paragraph and in the main loop. */
    private static final int COPIES = 2;

    private final JobRepository jobRepository;
    private final PrintSteps printSteps;

    public ReadXrefJobConfiguration(JobRepository jobRepository, PrintSteps printSteps) {
        this.jobRepository = jobRepository;
        this.printSteps = printSteps;
    }

    @Bean
    public Job readXrefJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(printSteps.<CardXrefRecord>printStep(JOB_NAME, STEP_NAME, PROGRAM,
                        "select x from CardXrefRecord x order by x.cardNum",
                        RecordImages::cardXref, COPIES,
                        OPEN_ERROR, READ_ERROR, CLOSE_ERROR))
                .build();
    }
}
