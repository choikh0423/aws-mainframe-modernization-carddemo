package com.carddemo.common.batch;

/**
 * Thrown where a COBOL batch program issues {@code CALL 'CEE3ABD'} (boundary
 * B-01). Nothing catches it: it fails the step, Spring Batch records the
 * failure in the JobRepository so the job can be restarted, and
 * {@code com.carddemo.batch.BatchJobLauncher} turns it into a non-zero process
 * exit code after logging the {@link AbendData}.
 */
public class AbendException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final AbendData abendData;

    public AbendException(AbendData abendData) {
        super(abendData.toString());
        this.abendData = abendData;
    }

    public AbendData getAbendData() {
        return abendData;
    }
}
