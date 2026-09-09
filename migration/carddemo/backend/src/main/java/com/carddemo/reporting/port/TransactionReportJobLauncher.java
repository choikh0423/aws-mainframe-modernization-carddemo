package com.carddemo.reporting.port;

/**
 * The S-07 → S-14 seam. CORPT00C submits the TRANREPT job by writing its JCL
 * deck to the CICS internal reader through extra-partition TDQ {@code JOBS}
 * (cbl:498-523, app/jcl/INTRDRJ1.JCL); in the target the online stream launches
 * the Spring Batch job that stream S-14 TransactionReporting owns in
 * {@code com.carddemo.batch.tranreport}.
 *
 * <p>This port is deliberately the whole contract between the two streams: the
 * report name and the date range, nothing else. S-14 supplies a {@code @Primary}
 * implementation that starts its job; until it exists the context binds
 * {@link com.carddemo.reporting.adapter.NoOpTransactionReportJobLauncher}, which
 * records the request and reports success so the screen behaves exactly as it
 * will once the real job arrives (see the migration plan §3).
 */
public interface TransactionReportJobLauncher {

    /**
     * Submit the TRANREPT report job for the given range.
     *
     * @throws RuntimeException if the job could not be submitted; CR00 turns that
     *         into the legacy {@code Unable to Write TDQ (JOBS)...} error (FR-R35)
     */
    void launch(TransactionReportJobRequest request);
}
