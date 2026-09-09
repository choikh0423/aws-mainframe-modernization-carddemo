package com.carddemo.batch.tranreport;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * The launch port for TRANREPT: the in-process replacement for the CICS
 * internal reader the CR00 report screen submitted the job through
 * (app/cbl/CORPT00C.cbl:441-479).
 *
 * <p>CR00 builds the job's JCL from the report type it derived from the screen
 * ({@code Monthly}, {@code Yearly} or {@code Custom}, CORPT00C.cbl:214, 240,
 * 433) and the start and end dates it validated, writes it to the JOBS transient
 * data queue and tells the user the job was submitted. Stream S-07 owns that
 * screen; this service is the boundary it calls, and it deliberately re-validates
 * nothing - CBTRN03C never saw the screen's edits either.
 *
 * <p>Every submission is a new job instance: the internal reader started a fresh
 * job every time the user pressed enter, even for a range that had already been
 * reported, so a {@code submittedAt} parameter makes the instance unique.
 */
@Service
public class TransactionReportLauncher {

    /** Makes each submission its own job instance, as a new reader submission was. */
    public static final String PARAM_SUBMITTED_AT = "submittedAt";

    private final JobLauncher jobLauncher;
    private final Job tranReportJob;

    public TransactionReportLauncher(JobLauncher jobLauncher,
                                     @Qualifier("tranReportJob") Job tranReportJob) {
        this.jobLauncher = jobLauncher;
        this.tranReportJob = tranReportJob;
    }

    /**
     * Submits TRANREPT for a report type and an inclusive date range, both
     * {@code YYYY-MM-DD}, and runs it to completion.
     *
     * @throws org.springframework.batch.core.JobParametersInvalidException if the
     *         report type or either date is missing
     */
    public TransactionReportSubmission submit(String reportType, String startDate, String endDate)
            throws JobExecutionAlreadyRunningException, JobRestartException,
            JobInstanceAlreadyCompleteException, JobParametersInvalidException {
        JobParameters parameters = new JobParametersBuilder()
                .addString(TranReportJobConfiguration.PARAM_REPORT_TYPE, reportType)
                .addString(TranReportJobConfiguration.PARAM_START_DATE, startDate)
                .addString(TranReportJobConfiguration.PARAM_END_DATE, endDate)
                .addString(PARAM_SUBMITTED_AT, LocalDateTime.now().toString())
                .toJobParameters();
        JobExecution execution = jobLauncher.run(tranReportJob, parameters);
        return new TransactionReportSubmission(
                execution.getId(),
                execution.getStatus().name(),
                execution.getExecutionContext().getString(TranReportFiles.REPORT_FILE, null));
    }
}
