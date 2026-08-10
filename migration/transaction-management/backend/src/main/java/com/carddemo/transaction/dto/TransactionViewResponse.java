package com.carddemo.transaction.dto;

import com.carddemo.transaction.entity.TransactionRecord;
import com.carddemo.transaction.util.CobolFormat;

import java.math.BigDecimal;

/**
 * Read-only view of a single transaction, mirroring the fields COTRN01C moves
 * onto the COTRN1A map in PROCESS-ENTER-KEY (COTRN01C.cbl:176-192). Every
 * CVTRA05Y display field is surfaced; {@code amountDisplay} carries the legacy
 * {@code PIC +99999999.99} editing while {@code amount} keeps the raw numeric.
 */
public class TransactionViewResponse {

    private final String id;
    private final String cardNum;
    private final String typeCd;
    private final Integer catCd;
    private final String source;
    private final BigDecimal amount;
    private final String amountDisplay;
    private final String description;
    private final String origTs;
    private final String procTs;
    private final Long merchantId;
    private final String merchantName;
    private final String merchantCity;
    private final String merchantZip;

    private TransactionViewResponse(TransactionRecord r) {
        this.id = r.getId();
        this.cardNum = r.getCardNum();
        this.typeCd = r.getTypeCd();
        this.catCd = r.getCatCd();
        this.source = r.getSource();
        this.amount = r.getAmount();
        this.amountDisplay = CobolFormat.amountEdited(r.getAmount());
        this.description = r.getDescription();
        this.origTs = r.getOrigTs();
        this.procTs = r.getProcTs();
        this.merchantId = r.getMerchantId();
        this.merchantName = r.getMerchantName();
        this.merchantCity = r.getMerchantCity();
        this.merchantZip = r.getMerchantZip();
    }

    public static TransactionViewResponse from(TransactionRecord record) {
        return new TransactionViewResponse(record);
    }

    public String getId() { return id; }
    public String getCardNum() { return cardNum; }
    public String getTypeCd() { return typeCd; }
    public Integer getCatCd() { return catCd; }
    public String getSource() { return source; }
    public BigDecimal getAmount() { return amount; }
    public String getAmountDisplay() { return amountDisplay; }
    public String getDescription() { return description; }
    public String getOrigTs() { return origTs; }
    public String getProcTs() { return procTs; }
    public Long getMerchantId() { return merchantId; }
    public String getMerchantName() { return merchantName; }
    public String getMerchantCity() { return merchantCity; }
    public String getMerchantZip() { return merchantZip; }
}
