package com.carddemo.batch.model;

/**
 * POJO for rejected transaction records.
 * Migrated from CBTRN02C.cbl REJECT-RECORD structure.
 * Contains the original 350-char transaction data + 80-char validation trailer.
 */
public class RejectRecord {

    private String originalTransactionData;
    private int reasonCode;
    private String reasonDescription;

    public RejectRecord() {
    }

    public RejectRecord(String originalTransactionData, int reasonCode, String reasonDescription) {
        this.originalTransactionData = originalTransactionData;
        this.reasonCode = reasonCode;
        this.reasonDescription = reasonDescription;
    }

    public String getOriginalTransactionData() {
        return originalTransactionData;
    }

    public void setOriginalTransactionData(String originalTransactionData) {
        this.originalTransactionData = originalTransactionData;
    }

    public int getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(int reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getReasonDescription() {
        return reasonDescription;
    }

    public void setReasonDescription(String reasonDescription) {
        this.reasonDescription = reasonDescription;
    }

    /**
     * Returns the 430-character reject record (350 data + 80 trailer)
     * matching the COBOL DALYREJS file format.
     */
    public String toFixedLengthString() {
        String data = originalTransactionData != null ? originalTransactionData : "";
        if (data.length() < 350) {
            data = String.format("%-350s", data);
        }
        String trailer = String.format("%04d%-76s", reasonCode,
                reasonDescription != null ? reasonDescription : "");
        return data + trailer;
    }
}
