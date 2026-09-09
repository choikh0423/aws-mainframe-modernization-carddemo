package com.carddemo.account.exception;

/** READ ... UPDATE failed (COACTUPC:3892-3944). */
public class RecordLockException extends RuntimeException {

    public RecordLockException(String message) {
        super(message);
    }
}
