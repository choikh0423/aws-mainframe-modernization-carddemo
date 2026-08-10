package com.carddemo.transaction.dto;

/**
 * Success payload for a written transaction. {@code message} carries the exact
 * legacy green ERRMSG text COTRN02C builds after a successful WRITE
 * (COTRN02C.cbl:726-734); {@code tranId} is the generated 16-digit key
 * (max existing + 1, COTRN02C.cbl:444-451).
 */
public class TransactionAddResponse {

    private final String tranId;
    private final String message;

    public TransactionAddResponse(String tranId, String message) {
        this.tranId = tranId;
        this.message = message;
    }

    public String getTranId() { return tranId; }
    public String getMessage() { return message; }
}
