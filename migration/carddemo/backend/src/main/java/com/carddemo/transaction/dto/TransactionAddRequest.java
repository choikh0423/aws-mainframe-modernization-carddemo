package com.carddemo.transaction.dto;

/**
 * Add-transaction request carrying the CT02 screen fields exactly as typed on
 * map COTRN2A (COTRN02C). Every field is a raw String so the backend can apply
 * the same edit rules COTRN02C's VALIDATE-INPUT-* paragraphs applied to the
 * 3270 input fields (empty / numeric / format checks) before the record is
 * built. Lengths mirror the BMS field widths in app/bms/COTRN02.bms.
 */
public class TransactionAddRequest {

    /** ACTIDIN — Account ID (numeric, 11); resolves the card via CARDXREF (FR-A1). */
    private String accountId;
    /** CARDNIN — Card Number (16); resolves the account via CARDXREF (FR-A2). */
    private String cardNumber;
    /** TTYPCD — Transaction type code (numeric, 2). */
    private String typeCd;
    /** TCATCD — Transaction category code (numeric, 4). */
    private String categoryCd;
    /** TRNSRC — Transaction source (10). */
    private String source;
    /** TDESC — Description (60 on the map, stored in TRAN-DESC X(100)). */
    private String description;
    /** TRNAMT — Amount in the fixed edit format {@code -99999999.99} (12). */
    private String amount;
    /** TORIGDT — Origination date {@code YYYY-MM-DD} (10). */
    private String origDate;
    /** TPROCDT — Processing date {@code YYYY-MM-DD} (10). */
    private String procDate;
    /** MID — Merchant ID (numeric, 9). */
    private String merchantId;
    /** MNAME — Merchant name (30). */
    private String merchantName;
    /** MCITY — Merchant city (25). */
    private String merchantCity;
    /** MZIP — Merchant zip (10). */
    private String merchantZip;
    /** CONFIRM — Y/N gate; must be Y/y to write (FR-A11). */
    private String confirm;

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }

    public String getTypeCd() { return typeCd; }
    public void setTypeCd(String typeCd) { this.typeCd = typeCd; }

    public String getCategoryCd() { return categoryCd; }
    public void setCategoryCd(String categoryCd) { this.categoryCd = categoryCd; }

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
