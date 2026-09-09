package com.carddemo.batch.statement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * One AWS.M2.CARDDEMO.TRXFL.VSAM.KSDS record: the card-keyed copy of TRANSACT
 * that CREASTMT.JCL builds (STEP010 SORT, STEP020 REPRO) and CBSTM03A reads
 * through TRNXFILE. Layout is app/cpy/COSTM01.CPY.
 *
 * <p>Stream-private to S-13: it is a work file, not part of the shared estate.
 */
@Entity
@Table(name = "statement_work_transactions")
@IdClass(StatementWorkTransactionKey.class)
public class StatementWorkTransaction {

    @Id
    @Column(name = "card_num", nullable = false, length = 16)
    private String cardNum;

    @Id
    @Column(name = "tran_id", nullable = false, length = 16)
    private String tranId;

    @Column(name = "type_cd", nullable = false, length = 2)
    private String typeCd;

    @Column(name = "cat_cd", nullable = false)
    private Integer catCd;

    @Column(name = "source", nullable = false, length = 10)
    private String source;

    @Column(name = "description", nullable = false, length = 100)
    private String description;

    @Column(name = "amount", nullable = false, precision = 11, scale = 2)
    private BigDecimal amount;

    @Column(name = "merchant_id", nullable = false, precision = 9)
    private Long merchantId;

    @Column(name = "merchant_name", nullable = false, length = 50)
    private String merchantName;

    @Column(name = "merchant_city", nullable = false, length = 50)
    private String merchantCity;

    @Column(name = "merchant_zip", nullable = false, length = 10)
    private String merchantZip;

    @Column(name = "orig_ts", length = 26)
    private String origTs;

    @Column(name = "proc_ts", length = 26)
    private String procTs;

    public String getCardNum() {
        return cardNum;
    }

    public void setCardNum(String cardNum) {
        this.cardNum = cardNum;
    }

    public String getTranId() {
        return tranId;
    }

    public void setTranId(String tranId) {
        this.tranId = tranId;
    }

    public String getTypeCd() {
        return typeCd;
    }

    public void setTypeCd(String typeCd) {
        this.typeCd = typeCd;
    }

    public Integer getCatCd() {
        return catCd;
    }

    public void setCatCd(Integer catCd) {
        this.catCd = catCd;
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

    public String getOrigTs() {
        return origTs;
    }

    public void setOrigTs(String origTs) {
        this.origTs = origTs;
    }

    public String getProcTs() {
        return procTs;
    }

    public void setProcTs(String procTs) {
        this.procTs = procTs;
    }
}
