package com.carddemo.transaction.dto;

/**
 * Result of the CT02 account/card cross-reference resolution
 * (COTRN02C VALIDATE-INPUT-KEY-FIELDS, COTRN02C.cbl:193-230): entering an
 * Account ID yields its Card Number (FR-A1) and entering a Card Number yields
 * its Account ID (FR-A2). Both are returned zero-padded to the legacy key
 * widths so the screen can echo the resolved partner field.
 */
public class CardXrefResolveResponse {

    private final String accountId;
    private final String cardNumber;

    public CardXrefResolveResponse(String accountId, String cardNumber) {
        this.accountId = accountId;
        this.cardNumber = cardNumber;
    }

    public String getAccountId() { return accountId; }
    public String getCardNumber() { return cardNumber; }
}
