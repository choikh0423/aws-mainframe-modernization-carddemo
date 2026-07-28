package com.carddemo.transaction.exception;

/**
 * Any COTRN02C path that sets WS-ERR-FLG to 'Y' and re-sends the map with a
 * message in ERRMSGO.
 */
public class TransactionValidationException extends RuntimeException {

    private final String field;

    public TransactionValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
