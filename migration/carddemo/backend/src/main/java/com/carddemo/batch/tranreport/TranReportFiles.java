package com.carddemo.batch.tranreport;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the datasets the TRANREPT job passes between its steps and publishes
 * them in the job execution context, so a caller (the S-07 launch port, an
 * operator, a test) can find the report a run produced.
 *
 * <p>The JCL allocates the two work files and the report as new generations of
 * GDG bases (app/jcl/TRANREPT.jcl:31, 54, 76): {@code TRANSACT.BKUP(+1)},
 * {@code TRANSACT.DALY(+1)} and {@code TRANREPT(+1)}. The generation number is
 * the job execution id, which gives every run its own set of files exactly as
 * {@code (+1)} did, and keeps previous runs readable.
 */
class TranReportFiles implements JobExecutionListener {

    static final String BACKUP_FILE = "tranreport.backupFile";
    static final String EXTRACT_FILE = "tranreport.extractFile";
    static final String REPORT_FILE = "tranreport.reportFile";

    private final Path outputDir;

    TranReportFiles(Path outputDir) {
        this.outputDir = outputDir;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the batch output directory " + outputDir, e);
        }
        String generation = String.format("G%04d", jobExecution.getId());
        jobExecution.getExecutionContext().putString(BACKUP_FILE,
                outputDir.resolve("TRANSACT.BKUP." + generation).toString());
        jobExecution.getExecutionContext().putString(EXTRACT_FILE,
                outputDir.resolve("TRANSACT.DALY." + generation).toString());
        jobExecution.getExecutionContext().putString(REPORT_FILE,
                outputDir.resolve("TRANREPT." + generation).toString());
    }
}
