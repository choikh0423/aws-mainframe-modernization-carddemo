package com.carddemo.transaction.exception;

/**
 * Raised when the Tran ID is empty/blank, mirroring COTRN01C's empty-key guard
 * in PROCESS-ENTER-KEY (COTRN01C.cbl:147-152): "Tran ID can NOT be empty...".
 */
public class EmptyTranIdException extends RuntimeException {

    public static final String MESSAGE = "Tran ID can NOT be empty...";

    public EmptyTranIdException() {
        super(MESSAGE);
    }
}
