package com.carddemo.transaction.exception;

/**
 * WRITE-TRANSACT-FILE failures other than a duplicate key.
 */
public class TransactionAddFailedException extends RuntimeException {

    public TransactionAddFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
