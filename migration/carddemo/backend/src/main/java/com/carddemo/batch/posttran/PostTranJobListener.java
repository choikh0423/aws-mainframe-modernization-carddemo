package com.carddemo.batch.posttran;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * The DISPLAY statements and the condition code of CBTRN02C's main line
 * (CBTRN02C.cbl:194, 227-232), plus the allocation of the DALYREJS generation
 * the JCL did for the step (POSTTRAN.jcl:34-38).
 *
 * <p>The reject file name is kept in the job execution context so a restart
 * appends to the generation the failed run allocated instead of rolling a new
 * one.
 */
@Component
public class PostTranJobListener implements JobExecutionListener, ExitCodeGenerator {

    /** Job execution context key holding the DALYREJS generation of this run. */
    public static final String REJECT_FILE_KEY = "posttran.dalyrejs.file";

    /** MOVE 4 TO RETURN-CODE when anything was rejected (CBTRN02C.cbl:229-231). */
    public static final int CONDITION_CODE_REJECTS = 4;

    private static final Logger log = LoggerFactory.getLogger(PostTranJobListener.class);

    private final RejectFileGenerations rejectFileGenerations;

    private volatile int conditionCode;

    public PostTranJobListener(RejectFileGenerations rejectFileGenerations) {
        this.rejectFileGenerations = rejectFileGenerations;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("START OF EXECUTION OF PROGRAM CBTRN02C");
        conditionCode = 0;
        if (!jobExecution.getExecutionContext().containsKey(REJECT_FILE_KEY)) {
            Path generation = rejectFileGenerations.allocateNextGeneration();
            jobExecution.getExecutionContext().putString(REJECT_FILE_KEY, generation.toString());
        }
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        long processed = 0;
        long rejected = 0;
        for (StepExecution step : jobExecution.getStepExecutions()) {
            processed += step.getReadCount();
            rejected += step.getWriteCount();
        }

        log.info("TRANSACTIONS PROCESSED :{}", count(processed));
        log.info("TRANSACTIONS REJECTED  :{}", count(rejected));
        if (rejected > 0 && jobExecution.getStatus() == BatchStatus.COMPLETED) {
            conditionCode = CONDITION_CODE_REJECTS;
        }
        log.info("END OF EXECUTION OF PROGRAM CBTRN02C");
    }

    /** PIC 9(09). */
    private static String count(long value) {
        return String.format("%09d", value);
    }

    /**
     * The step's condition code. A failed job is reported as 12 by
     * {@link com.carddemo.batch.BatchJobLauncher}, which wins over this one
     * because Boot takes the highest exit code of all generators.
     */
    @Override
    public int getExitCode() {
        return conditionCode;
    }
}
