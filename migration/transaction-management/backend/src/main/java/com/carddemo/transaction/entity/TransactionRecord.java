package com.carddemo.transaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * JPA entity mapping to CVTRA05Y TRAN-RECORD (350 bytes) -> table {@code transactions}.
 * Field names, types, and lengths follow the COBOL copybook. TRAN-ID is the
 * zero-padded 16-char numeric key ({@code id}), preserving VSAM key ordering.
 */
@Entity
@Table(name = "transactions")
public class TransactionRecord {

    @Id
    @Column(name = "id", length = 16, nullable = false)
    private String id;

    @Column(name = "type_cd", length = 2, nullable = false)
    private String typeCd;

    @Column(name = "cat_cd", nullable = false)
    private Integer catCd;

    @Column(name = "source", length = 10, nullable = false)
    private String source;

    @Column(name = "description", length = 100, nullable = false)
    private String description;

    @Column(name = "amount", precision = 11, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "merchant_name", length = 50, nullable = false)
    private String merchantName;

    @Column(name = "merchant_city", length = 50, nullable = false)
    private String merchantCity;

    @Column(name = "merchant_zip", length = 10, nullable = false)
    private String merchantZip;

    @Column(name = "card_num", length = 16, nullable = false)
    private String cardNum;

    @Column(name = "orig_ts", length = 26)
    private String origTs;

    @Column(name = "proc_ts", length = 26)
    private String procTs;

    public TransactionRecord() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTypeCd() { return typeCd; }
    public void setTypeCd(String typeCd) { this.typeCd = typeCd; }

    public Integer getCatCd() { return catCd; }
    public void setCatCd(Integer catCd) { this.catCd = catCd; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }

    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }

    public String getMerchantCity() { return merchantCity; }
    public void setMerchantCity(String merchantCity) { this.merchantCity = merchantCity; }

    public String getMerchantZip() { return merchantZip; }
    public void setMerchantZip(String merchantZip) { this.merchantZip = merchantZip; }

    public String getCardNum() { return cardNum; }
    public void setCardNum(String cardNum) { this.cardNum = cardNum; }

    public String getOrigTs() { return origTs; }
    public void setOrigTs(String origTs) { this.origTs = origTs; }

    public String getProcTs() { return procTs; }
    public void setProcTs(String procTs) { this.procTs = procTs; }
}
