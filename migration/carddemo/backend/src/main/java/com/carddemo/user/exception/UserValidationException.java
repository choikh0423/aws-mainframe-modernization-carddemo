package com.carddemo.user.exception;

/**
 * A CU01/CU02/CU03 field edit failed. Carries the verbatim ERRMSG literal of the
 * first failing check, reproducing the COBOL {@code EVALUATE TRUE} short-circuit
 * (COUSR01C.cbl:115-152).
 */
public class UserValidationException extends RuntimeException {

    public UserValidationException(String message) {
        super(message);
    }
}
