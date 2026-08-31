package com.carddemo.batch.listener;

import com.carddemo.batch.writer.RejectRecordWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * Listener for batch job completion reporting.
 * Ported from CBTRN02C.cbl lines 227-232: summary display and exit code handling.
 * Sets exit code to 4 if any rejections occurred.
 */
@Component
public class JobCompletionListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobCompletionListener.class);

    private final RejectRecordWriter rejectRecordWriter;

    public JobCompletionListener(RejectRecordWriter rejectRecordWriter) {
        this.rejectRecordWriter = rejectRecordWriter;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        rejectRecordWriter.resetCount();
        log.info("Job {} starting at {}", jobExecution.getJobInstance().getJobName(),
                jobExecution.getStartTime());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        int rejectCount = rejectRecordWriter.getRejectCount();
        long readCount = jobExecution.getStepExecutions().stream()
                .mapToLong(se -> se.getReadCount())
                .sum();
        long writeCount = jobExecution.getStepExecutions().stream()
                .mapToLong(se -> se.getWriteCount())
                .sum();
        long skipCount = jobExecution.getStepExecutions().stream()
                .mapToLong(se -> se.getSkipCount())
                .sum();

        log.info("=== JOB COMPLETION SUMMARY ===");
        log.info("Job: {}", jobExecution.getJobInstance().getJobName());
        log.info("Status: {}", jobExecution.getStatus());
        log.info("Transactions read:    {}", readCount);
        log.info("Transactions posted:  {}", writeCount);
        log.info("Transactions skipped: {}", skipCount);
        log.info("Reject records:       {}", rejectCount);

        if (jobExecution.getStatus() == BatchStatus.COMPLETED && rejectCount > 0) {
            // Set exit code 4 if rejections occurred (matching COBOL behavior)
            jobExecution.setExitStatus(new ExitStatus("COMPLETED_WITH_REJECTS",
                    "Job completed with " + rejectCount + " rejected transactions"));
            log.warn("Job completed with {} rejections - exit code 4", rejectCount);
        }

        log.info("==============================");
    }
}
