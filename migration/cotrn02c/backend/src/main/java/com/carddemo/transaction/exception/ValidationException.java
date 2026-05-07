package com.carddemo.transaction.exception;

/**
 * Thrown when a validation rule fails during transaction processing.
 * Each instance carries the exact COBOL error message text and the
 * field that should receive cursor focus (matching BMS CURSOR positioning).
 *
 * This replicates the COBOL pattern where SEND-TRNADD-SCREEN contains
 * EXEC CICS RETURN, terminating the task on the first validation error.
 */
public class ValidationException extends RuntimeException {

    private final String errorField;

    public ValidationException(String message, String errorField) {
        super(message);
        this.errorField = errorField;
    }

    public String getErrorField() {
        return errorField;
    }
}
