package com.carddemo.transaction.exception;

/**
 * Raised when a transaction key is not present, mirroring the DFHRESP(NOTFND)
 * branch of READ-TRANSACT-FILE in COTRN01C (COTRN01C.cbl:283-288):
 * "Transaction ID NOT found...".
 */
public class TransactionNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Transaction ID NOT found...";

    public TransactionNotFoundException() {
        super(MESSAGE);
    }
}
