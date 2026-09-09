package com.carddemo.transaction.exception;

/**
 * Raised when the generated Tran ID already exists, mirroring the
 * DFHRESP(DUPKEY)/DFHRESP(DUPREC) branch of WRITE-TRANSACT-FILE in COTRN02C
 * (COTRN02C.cbl:735-741): "Tran ID already exist...". Maps to HTTP 409.
 */
public class DuplicateTranIdException extends RuntimeException {

    public static final String MESSAGE = "Tran ID already exist...";

    public DuplicateTranIdException() {
        super(MESSAGE);
    }
}
