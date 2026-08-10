package com.carddemo.transaction.controller;

/**
 * Carries the COBOL error text so behaviour parity with COTRN02C is auditable.
 */
public class ErrorResponse {

    private final String message;
    private final String field;

    public ErrorResponse(String message, String field) {
        this.message = message;
        this.field = field;
    }

    public String getMessage() {
        return message;
    }

    public String getField() {
        return field;
    }
}
