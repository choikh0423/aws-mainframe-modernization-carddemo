package com.carddemo.transaction.exception;

/**
 * Raised for any CT02 input edit failure that COTRN02C surfaces as an ERRMSG and
 * a {@code PERFORM SEND-TRNADD-SCREEN} (which RETURNs before the WRITE) — i.e.
 * the empty / numeric / amount-format / date-format / date-validity / confirm
 * checks in VALIDATE-INPUT-KEY-FIELDS, VALIDATE-INPUT-DATA-FIELDS and
 * PROCESS-ENTER-KEY (COTRN02C.cbl:169-436). The message is the verbatim legacy
 * text; it maps to HTTP 400.
 */
public class TransactionValidationException extends RuntimeException {

    public TransactionValidationException(String message) {
        super(message);
    }
}
