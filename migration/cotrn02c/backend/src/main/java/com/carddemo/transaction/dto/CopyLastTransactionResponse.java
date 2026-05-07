package com.carddemo.transaction.dto;

/**
 * Response DTO for the PF5 "Copy Last Transaction" function.
 * Returns all 11 data fields from the most recent transaction record
 * so the frontend can pre-fill the form.
 *
 * Maps to COPY-LAST-TRAN-DATA paragraph (COTRN02C.cbl lines 471-495).
 */
public class CopyLastTransactionResponse {

    private boolean success;
    private String message;
    private String errorField;
    private String typeCd;
    private String catCd;
    private String source;
    private String description;
    private String amount;
    private String origDate;
    private String procDate;
    private String merchantId;
    private String merchantName;
    private String merchantCity;
    private String merchantZip;

    public CopyLastTransactionResponse() {
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getErrorField() { return errorField; }
    public void setErrorField(String errorField) { this.errorField = errorField; }

    public String getTypeCd() { return typeCd; }
    public void setTypeCd(String typeCd) { this.typeCd = typeCd; }

    public String getCatCd() { return catCd; }
    public void setCatCd(String catCd) { this.catCd = catCd; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }

    public String getOrigDate() { return origDate; }
    public void setOrigDate(String origDate) { this.origDate = origDate; }

    public String getProcDate() { return procDate; }
    public void setProcDate(String procDate) { this.procDate = procDate; }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }

    public String getMerchantCity() { return merchantCity; }
    public void setMerchantCity(String merchantCity) { this.merchantCity = merchantCity; }

    public String getMerchantZip() { return merchantZip; }
    public void setMerchantZip(String merchantZip) { this.merchantZip = merchantZip; }
}
