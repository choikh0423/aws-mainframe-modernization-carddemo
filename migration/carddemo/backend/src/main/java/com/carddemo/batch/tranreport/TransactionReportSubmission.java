package com.carddemo.batch.tranreport;

/**
 * What a caller gets back when it submits TRANREPT, the migrated equivalent of
 * the CR00 screen's {@code EXEC CICS WRITEQ TD QUEUE('JOBS')} plus the message
 * it then displays (app/cbl/CORPT00C.cbl:472-479).
 *
 * @param jobExecutionId the Spring Batch job execution id, standing in for the
 *                       job number the internal reader assigned
 * @param status         the batch status of the run
 * @param reportFile     the report the run produced, or {@code null} if the run
 *                       did not get that far
 */
public record TransactionReportSubmission(long jobExecutionId, String status, String reportFile) {
}
