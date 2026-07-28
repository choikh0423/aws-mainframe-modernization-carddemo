package com.carddemo.transaction.dto;

/**
 * Equivalent of the ERRMSGO field on map COTRN2A plus the generated key.
 */
public class AddTransactionResponse {

    private final String message;
    private final String tranId;
    private final boolean added;

    private AddTransactionResponse(String message, String tranId, boolean added) {
        this.message = message;
        this.tranId = tranId;
        this.added = added;
    }

    public static AddTransactionResponse added(String tranId) {
        return new AddTransactionResponse(
                "Transaction added successfully.  Your Tran ID is " + tranId + ".", tranId, true);
    }

    public static AddTransactionResponse confirmationRequired() {
        return new AddTransactionResponse("Confirm to add this transaction...", null, false);
    }

    public String getMessage() {
        return message;
    }

    public String getTranId() {
        return tranId;
    }

    public boolean isAdded() {
        return added;
    }
}
