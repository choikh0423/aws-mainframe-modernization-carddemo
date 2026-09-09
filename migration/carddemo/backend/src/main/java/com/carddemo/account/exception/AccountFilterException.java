package com.carddemo.account.exception;

/** The account id edit failed (COACTVWC:649-676, COACTUPC:1783-1818). */
public class AccountFilterException extends RuntimeException {

    public AccountFilterException(String message) {
        super(message);
    }
}
