package com.carddemo.account.exception;

/** A REWRITE failed; the unit of work is rolled back (COACTUPC:4065-4103). */
public class AccountUpdateFailedException extends RuntimeException {

    public AccountUpdateFailedException(String message) {
        super(message);
    }
}
