package com.carddemo.batch.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * JPA entity for the Transaction record.
 * Migrated from COBOL copybook CVTRA05Y.cpy (RECLN 350).
 * VSAM KSDS key: TRAN-ID PIC X(16).
 */
@Entity
@Table(name = "transaction")
public class Transaction {

    @Id
    @Column(name = "tran_id", length = 16)
    private String tranId;

    @Column(name = "tran_type_cd", length = 2)
    private String tranTypeCd;

    @Column(name = "tran_cat_cd")
    private Integer tranCatCd;

    @Column(name = "tran_source", length = 10)
    private String tranSource;

    @Column(name = "tran_desc", length = 100)
    private String tranDesc;

    @Column(name = "tran_amt", precision = 11, scale = 2)
    private BigDecimal tranAmt;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "merchant_name", length = 50)
    private String merchantName;

    @Column(name = "merchant_city", length = 50)
    private String merchantCity;

    @Column(name = "merchant_zip", length = 10)
    private String merchantZip;

    @Column(name = "card_num", length = 16)
    private String cardNum;

    @Column(name = "orig_timestamp", length = 26)
    private String origTimestamp;

    @Column(name = "proc_timestamp", length = 26)
    private String procTimestamp;

    public Transaction() {
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
}
