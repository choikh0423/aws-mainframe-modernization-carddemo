package com.carddemo.account.exception;

/** A xref, account or customer read returned NOTFND (COACTVWC:723-870). */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String message) {
        super(message);
    }
}
