package com.carddemo.batch.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

/**
 * REST controller for launching batch jobs.
 * Provides POST endpoints for each migrated COBOL batch program.
 * Also supports command-line execution via spring.batch.job.name property.
 */
@RestController
@RequestMapping("/api/jobs")
public class JobLauncherController {

    private static final Logger log = LoggerFactory.getLogger(JobLauncherController.class);

    private final JobLauncher jobLauncher;
    private final Job postTransactionJob;
    private final Job transactionReportJob;
    private final Job interestCalcJob;
    private final Job combineTransactionJob;
    private final Job transactionBackupJob;
    private final Job readAccountJob;
    private final Job readCardJob;
    private final Job readCustomerJob;
    private final Job readXrefJob;
    private final Job exportJob;
    private final Job importJob;

    public JobLauncherController(
            JobLauncher jobLauncher,
            @Qualifier("postTransactionJob") Job postTransactionJob,
            @Qualifier("transactionReportJob") Job transactionReportJob,
            @Qualifier("interestCalcJob") Job interestCalcJob,
            @Qualifier("combineTransactionJob") Job combineTransactionJob,
            @Qualifier("transactionBackupJob") Job transactionBackupJob,
            @Qualifier("readAccountJob") Job readAccountJob,
            @Qualifier("readCardJob") Job readCardJob,
            @Qualifier("readCustomerJob") Job readCustomerJob,
            @Qualifier("readXrefJob") Job readXrefJob,
            @Qualifier("exportJob") Job exportJob,
            @Qualifier("importJob") Job importJob) {
        this.jobLauncher = jobLauncher;
        this.postTransactionJob = postTransactionJob;
        this.transactionReportJob = transactionReportJob;
        this.interestCalcJob = interestCalcJob;
        this.combineTransactionJob = combineTransactionJob;
        this.transactionBackupJob = transactionBackupJob;
        this.readAccountJob = readAccountJob;
        this.readCardJob = readCardJob;
        this.readCustomerJob = readCustomerJob;
        this.readXrefJob = readXrefJob;
        this.exportJob = exportJob;
        this.importJob = importJob;
    }

    @PostMapping("/post-transactions")
    public ResponseEntity<String> postTransactions(
            @RequestParam(required = false) String dailyTranFile) {
        return launchJob(postTransactionJob, "postTransactionJob",
                new JobParametersBuilder()
                        .addDate("runDate", new Date())
                        .addString("dailyTranFile", dailyTranFile != null ? dailyTranFile : "")
                        .toJobParameters());
    }

    @PostMapping("/transaction-report")
    public ResponseEntity<String> transactionReport(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        return launchJob(transactionReportJob, "transactionReportJob",
                new JobParametersBuilder()
                        .addDate("runDate", new Date())
                        .addString("startDate", startDate)
                        .addString("endDate", endDate)
                        .toJobParameters());
    }

    @PostMapping("/interest-calc")
    public ResponseEntity<String> interestCalc(
            @RequestParam String processDate) {
        return launchJob(interestCalcJob, "interestCalcJob",
                new JobParametersBuilder()
                        .addDate("runDate", new Date())
                        .addString("processDate", processDate)
                        .toJobParameters());
    }

    @PostMapping("/combine-transactions")
    public ResponseEntity<String> combineTransactions(
            @RequestParam(required = false) String inputFile) {
        return launchJob(combineTransactionJob, "combineTransactionJob",
                new JobParametersBuilder()
                        .addDate("runDate", new Date())
                        .addString("inputFile", inputFile != null ? inputFile : "")
                        .toJobParameters());
    }

    @PostMapping("/transaction-backup")
    public ResponseEntity<String> transactionBackup(
            @RequestParam(required = false) String backupFile) {
        return launchJob(transactionBackupJob, "transactionBackupJob",
                new JobParametersBuilder()
                        .addDate("runDate", new Date())
                        .addString("backupFile", backupFile != null ? backupFile : "")
                        .toJobParameters());
    }

    @PostMapping("/read-accounts")
    public ResponseEntity<String> readAccounts(
            @RequestParam(required = false) String outputFile) {
        return launchJob(readAccountJob, "readAccountJob",
                new JobParametersBuilder()
                        .addDate("runDate", new Date())
                        .addString("outputFile", outputFile != null ? outputFile : "")
                        .toJobParameters());
    }

    @PostMapping("/read-cards")
    public ResponseEntity<String> readCards() {
        return launchJob(readCardJob, "readCardJob",
                new JobParametersBuilder()
                        .addDate("runDate", new Date())
                        .toJobParameters());
    }

    @PostMapping("/read-customers")
    public ResponseEntity<String> readCustomers() {
        return launchJob(readCustomerJob, "readCustomerJob",
                new JobParametersBuilder()
                        .addDate("runDate", new Date())
                        .toJobParameters());
    }

    @PostMapping("/read-xrefs")
    public ResponseEntity<String> readXrefs() {
        return launchJob(readXrefJob, "readXrefJob",
                new JobParametersBuilder()
                        .addDate("runDate", new Date())
                        .toJobParameters());
    }

    @PostMapping("/export")
    public ResponseEntity<String> exportData(
            @RequestParam(required = false) String exportFile) {
        return launchJob(exportJob, "exportJob",
                new JobParametersBuilder()
                        .addDate("runDate", new Date())
                        .addString("exportFile", exportFile != null ? exportFile : "")
                        .toJobParameters());
    }

    @PostMapping("/import")
    public ResponseEntity<String> importData(
            @RequestParam(required = false) String importFile,
            @RequestParam(required = false) String errorFile) {
        return launchJob(importJob, "importJob",
                new JobParametersBuilder()
                        .addDate("runDate", new Date())
                        .addString("importFile", importFile != null ? importFile : "")
                        .addString("errorFile", errorFile != null ? errorFile : "")
                        .toJobParameters());
    }

    private ResponseEntity<String> launchJob(Job job, String jobName, JobParameters params) {
        try {
            log.info("Launching job: {}", jobName);
            jobLauncher.run(job, params);
            return ResponseEntity.ok("Job " + jobName + " launched successfully");
        } catch (Exception e) {
            log.error("Failed to launch job {}: {}", jobName, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Failed to launch job " + jobName + ": " + e.getMessage());
        }
    }
}
