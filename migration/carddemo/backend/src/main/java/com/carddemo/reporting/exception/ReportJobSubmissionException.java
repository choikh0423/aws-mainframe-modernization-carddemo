package com.carddemo.reporting.exception;

/**
 * The submission itself failed — the target-side equivalent of a non-NORMAL
 * RESP from {@code WRITEQ TD QUEUE('JOBS')} (cbl:517-535). The message is the
 * legacy {@code Unable to Write TDQ (JOBS)...} text.
 */
public class ReportJobSubmissionException extends RuntimeException {

    private final String cursor;

    public ReportJobSubmissionException(String message, String cursor, Throwable cause) {
        super(message, cause);
        this.cursor = cursor;
    }

    public String getCursor() {
        return cursor;
    }
}
