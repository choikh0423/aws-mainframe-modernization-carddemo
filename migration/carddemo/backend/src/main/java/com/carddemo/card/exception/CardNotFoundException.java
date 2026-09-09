package com.carddemo.card.exception;

/**
 * The CARDDAT read returned NOTFND (COCRDSLC.cbl:755-761, COCRDUPC.cbl:1395-1401):
 * "Did not find cards for this search condition".
 */
public class CardNotFoundException extends RuntimeException {

    public CardNotFoundException(String message) {
        super(message);
    }
}
