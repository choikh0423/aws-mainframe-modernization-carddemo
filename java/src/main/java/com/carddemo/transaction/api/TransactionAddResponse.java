package com.carddemo.transaction.api;

/**
 * Result of one CT02 interaction: the message that COTRN02C would place in
 * ERRMSG, whether the transaction was written, and the (re-)displayed fields.
 */
public class TransactionAddResponse {

    private boolean error;
    private boolean added;
    private String message;
    private String tranId;
    private TransactionAddRequest fields;

    public static TransactionAddResponse error(String message, TransactionAddRequest fields) {
        TransactionAddResponse r = new TransactionAddResponse();
        r.error = true;
        r.message = message;
        r.fields = fields;
        return r;
    }

    public static TransactionAddResponse success(String message, String tranId, TransactionAddRequest fields) {
        TransactionAddResponse r = new TransactionAddResponse();
        r.added = true;
        r.message = message;
        r.tranId = tranId;
        r.fields = fields;
        return r;
    }

    public boolean isError() {
        return error;
    }

    public boolean isAdded() {
        return added;
    }

    public String getMessage() {
        return message;
    }

    public String getTranId() {
        return tranId;
    }

    public TransactionAddRequest getFields() {
        return fields;
    }
}
