package com.carddemo.batch.model;

import java.math.BigDecimal;

/**
 * POJO for the Daily Transaction record (input flat file).
 * Migrated from COBOL copybook CVTRA06Y.cpy (RECLN 350).
 * Not a JPA entity — used as a DTO for reading flat file input.
 */
public class DailyTransaction {

    private String tranId;
    private String tranTypeCd;
    private Integer tranCatCd;
    private String tranSource;
    private String tranDesc;
    private BigDecimal tranAmt;
    private Long merchantId;
    private String merchantName;
    private String merchantCity;
    private String merchantZip;
    private String cardNum;
    private String origTimestamp;
    private String procTimestamp;

    public DailyTransaction() {
    }

    public String getTranId() {
        return tranId;
    }

    public void setTranId(String tranId) {
        this.tranId = tranId;
    }

    public String getTranTypeCd() {
        return tranTypeCd;
    }

    public void setTranTypeCd(String tranTypeCd) {
        this.tranTypeCd = tranTypeCd;
    }

    public Integer getTranCatCd() {
        return tranCatCd;
    }

    public void setTranCatCd(Integer tranCatCd) {
        this.tranCatCd = tranCatCd;
    }

    public String getTranSource() {
        return tranSource;
    }

    public void setTranSource(String tranSource) {
        this.tranSource = tranSource;
    }

    public String getTranDesc() {
        return tranDesc;
    }

    public void setTranDesc(String tranDesc) {
        this.tranDesc = tranDesc;
    }

    public BigDecimal getTranAmt() {
        return tranAmt;
    }

    public void setTranAmt(BigDecimal tranAmt) {
        this.tranAmt = tranAmt;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public String getMerchantCity() {
        return merchantCity;
    }

    public void setMerchantCity(String merchantCity) {
        this.merchantCity = merchantCity;
    }

    public String getMerchantZip() {
        return merchantZip;
    }

    public void setMerchantZip(String merchantZip) {
        this.merchantZip = merchantZip;
    }

    public String getCardNum() {
        return cardNum;
    }

    public void setCardNum(String cardNum) {
        this.cardNum = cardNum;
    }

    public String getOrigTimestamp() {
        return origTimestamp;
    }

    public void setOrigTimestamp(String origTimestamp) {
        this.origTimestamp = origTimestamp;
    }

    public String getProcTimestamp() {
        return procTimestamp;
    }

    public void setProcTimestamp(String procTimestamp) {
        this.procTimestamp = procTimestamp;
    }

    /**
     * Returns the full 350-character fixed-length representation of this record
     * for use in reject file output.
     */
    public String toFixedLengthString() {
        return String.format("%-16s%-2s%04d%-10s%-100s%+012.2f%09d%-50s%-50s%-10s%-16s%-26s%-26s%-20s",
                safe(tranId, 16),
                safe(tranTypeCd, 2),
                tranCatCd != null ? tranCatCd : 0,
                safe(tranSource, 10),
                safe(tranDesc, 100),
                tranAmt != null ? tranAmt.doubleValue() : 0.0,
                merchantId != null ? merchantId : 0L,
                safe(merchantName, 50),
                safe(merchantCity, 50),
                safe(merchantZip, 10),
                safe(cardNum, 16),
                safe(origTimestamp, 26),
                safe(procTimestamp, 26),
                "");
    }

    private String safe(String value, int maxLen) {
        if (value == null) return "";
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }
}
