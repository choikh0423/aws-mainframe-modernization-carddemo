package com.carddemo.transaction.dto;

/**
 * DTO representing the input fields from BMS map COTRN2A.
 * Field names match the BMS field names from COTRN02.bms.
 *
 * BMS input fields mapped:
 *   ACTIDIN  -> acctId       (11 chars)
 *   CARDNIN  -> cardNum      (16 chars)
 *   TTYPCD   -> typeCd       (2 chars)
 *   TCATCD   -> catCd        (4 chars)
 *   TRNSRC   -> source       (10 chars)
 *   TDESC    -> description  (60 chars)
 *   TRNAMT   -> amount       (12 chars, format: +99999999.99)
 *   TORIGDT  -> origDate     (10 chars, format: YYYY-MM-DD)
 *   TPROCDT  -> procDate     (10 chars, format: YYYY-MM-DD)
 *   MID      -> merchantId   (9 chars)
 *   MNAME    -> merchantName (30 chars)
 *   MCITY    -> merchantCity (25 chars)
 *   MZIP     -> merchantZip  (10 chars)
 *   CONFIRM  -> confirm      (1 char, Y/N)
 */
public class AddTransactionRequest {

    private String acctId;
    private String cardNum;
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
    private String confirm;

    public AddTransactionRequest() {
    }

    public String getAcctId() { return acctId; }
    public void setAcctId(String acctId) { this.acctId = acctId; }

    public String getCardNum() { return cardNum; }
    public void setCardNum(String cardNum) { this.cardNum = cardNum; }

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

    public String getConfirm() { return confirm; }
    public void setConfirm(String confirm) { this.confirm = confirm; }
}
