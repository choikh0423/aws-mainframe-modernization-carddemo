package com.carddemo.reporting.exception;

import com.carddemo.reporting.dto.CustomDateFields;

/**
 * A CR00 edit failure: CORPT00C's {@code SEND-TRNRPT-SCREEN} followed by
 * {@code GO TO RETURN-TO-CICS} (cbl:556-580), which ends the task at the first
 * failing check (FR-R11). Throwing here reproduces that: nothing after the
 * failing edit runs and no job is launched.
 */
public class ReportValidationException extends RuntimeException {

    private final String cursor;
    private final transient CustomDateFields dateFields;

    public ReportValidationException(String message, String cursor) {
        this(message, cursor, null);
    }

    public ReportValidationException(String message, String cursor, CustomDateFields dateFields) {
        super(message);
        this.cursor = cursor;
        this.dateFields = dateFields;
    }

    public String getCursor() {
        return cursor;
    }

    /** The NUMVAL-C-normalised fields to redisplay, or null if normalisation had not run yet. */
    public CustomDateFields getDateFields() {
        return dateFields;
    }
}
