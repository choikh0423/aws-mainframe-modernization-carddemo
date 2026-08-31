package com.carddemo.transaction.dto;

/**
 * Response DTO for the Add Transaction endpoint.
 * Maps to the COBOL output behavior:
 *   - On success: green message "Transaction added successfully. Your Tran ID is {id}."
 *   - On error: red message with the specific error text
 *   - resolvedCardNum / resolvedAcctId: auto-populated from XREF lookup
 */
public class AddTransactionResponse {

    private boolean success;
    private String message;
    private String errorField;
    private String tranId;
    private String resolvedCardNum;
    private String resolvedAcctId;
    private String normalizedAmount;

    public AddTransactionResponse() {
    }

    public static AddTransactionResponse success(String tranId, String message) {
        AddTransactionResponse r = new AddTransactionResponse();
        r.success = true;
        r.tranId = tranId;
        r.message = message;
        return r;
    }

    public static AddTransactionResponse error(String message, String errorField) {
        AddTransactionResponse r = new AddTransactionResponse();
        r.success = false;
        r.message = message;
        r.errorField = errorField;
        return r;
    }

    public static AddTransactionResponse validated(String resolvedCardNum, String resolvedAcctId,
                                                    String normalizedAmount) {
        AddTransactionResponse r = new AddTransactionResponse();
        r.success = true;
        r.message = "Confirm to add this transaction...";
        r.resolvedCardNum = resolvedCardNum;
        r.resolvedAcctId = resolvedAcctId;
        r.normalizedAmount = normalizedAmount;
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getErrorField() { return errorField; }
    public void setErrorField(String errorField) { this.errorField = errorField; }

    public String getTranId() { return tranId; }
    public void setTranId(String tranId) { this.tranId = tranId; }

    public String getResolvedCardNum() { return resolvedCardNum; }
    public void setResolvedCardNum(String resolvedCardNum) { this.resolvedCardNum = resolvedCardNum; }

    public String getResolvedAcctId() { return resolvedAcctId; }
    public void setResolvedAcctId(String resolvedAcctId) { this.resolvedAcctId = resolvedAcctId; }

    public String getNormalizedAmount() { return normalizedAmount; }
    public void setNormalizedAmount(String normalizedAmount) { this.normalizedAmount = normalizedAmount; }
}
