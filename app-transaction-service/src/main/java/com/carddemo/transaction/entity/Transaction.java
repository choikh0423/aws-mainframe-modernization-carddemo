package com.carddemo.transaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * TRANSACT record, copybook CVTRA05Y (TRAN-RECORD, RECLN 350).
 */
@Entity
@Table(name = "transact")
public class Transaction {

    @Id
    @Column(name = "tran_id", length = 16, nullable = false)
    private String tranId;

    @Column(name = "tran_type_cd", length = 2)
    private String typeCode;

    @Column(name = "tran_cat_cd")
    private Integer categoryCode;

    @Column(name = "tran_source", length = 10)
    private String source;

    @Column(name = "tran_desc", length = 100)
    private String description;

    @Column(name = "tran_amt", precision = 11, scale = 2)
    private BigDecimal amount;

    @Column(name = "tran_merchant_id")
    private Long merchantId;

    @Column(name = "tran_merchant_name", length = 50)
    private String merchantName;

    @Column(name = "tran_merchant_city", length = 50)
    private String merchantCity;

    @Column(name = "tran_merchant_zip", length = 10)
    private String merchantZip;

    @Column(name = "tran_card_num", length = 16)
    private String cardNumber;

    @Column(name = "tran_orig_ts", length = 26)
    private String origTimestamp;

    @Column(name = "tran_proc_ts", length = 26)
    private String procTimestamp;

    public String getTranId() {
        return tranId;
    }

    public void setTranId(String tranId) {
        this.tranId = tranId;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public Integer getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(Integer categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
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

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
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
