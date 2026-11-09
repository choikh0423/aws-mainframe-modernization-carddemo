package com.carddemo.billpayment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** TRANSACT record (CVTRA05Y TRAN-RECORD). */
@Entity
@Table(name = "transact")
public class Transaction {
    @Id
    @Column(name = "tran_id", length = 16)
    private String tranId;
    private String typeCd;
    private Integer catCd;
    private String source;
    private String description;
    private BigDecimal amount;
    private Long merchantId;
    private String merchantName;
    private String merchantCity;
    private String merchantZip;
    private String cardNum;
    private String origTs;
    private String procTs;

    public String getTranId() { return tranId; }
    public void setTranId(String v) { tranId = v; }
    public String getTypeCd() { return typeCd; }
    public void setTypeCd(String v) { typeCd = v; }
    public Integer getCatCd() { return catCd; }
    public void setCatCd(Integer v) { catCd = v; }
    public String getSource() { return source; }
    public void setSource(String v) { source = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { amount = v; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long v) { merchantId = v; }
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String v) { merchantName = v; }
    public String getMerchantCity() { return merchantCity; }
    public void setMerchantCity(String v) { merchantCity = v; }
    public String getMerchantZip() { return merchantZip; }
    public void setMerchantZip(String v) { merchantZip = v; }
    public String getCardNum() { return cardNum; }
    public void setCardNum(String v) { cardNum = v; }
    public String getOrigTs() { return origTs; }
    public void setOrigTs(String v) { origTs = v; }
    public String getProcTs() { return procTs; }
    public void setProcTs(String v) { procTs = v; }
}
