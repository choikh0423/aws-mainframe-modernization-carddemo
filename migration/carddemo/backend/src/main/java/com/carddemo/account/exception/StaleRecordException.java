package com.carddemo.account.exception;

/** 9700-CHECK-CHANGE-IN-REC found the row changed since the fetch (COACTUPC:4109-4202). */
public class StaleRecordException extends RuntimeException {

    public StaleRecordException(String message) {
        super(message);
    }
}
