package com.carddemo.transaction.dto;

/**
 * Input fields of BMS map COTRN2A (mapset COTRN02) received by COTRN02C.
 */
public class AddTransactionRequest {

    /** ACTIDINI */
    private String accountId;
    /** CARDNINI */
    private String cardNumber;
    /** TTYPCDI */
    private String typeCode;
    /** TCATCDI */
    private String categoryCode;
    /** TRNSRCI */
    private String source;
    /** TDESCI */
    private String description;
    /** TRNAMTI - kept as a string so the COBOL -99999999.99 format check applies. */
    private String amount;
    /** TORIGDTI */
    private String origDate;
    /** TPROCDTI */
    private String procDate;
    /** MIDI */
    private String merchantId;
    /** MNAMEI */
    private String merchantName;
    /** MCITYI */
    private String merchantCity;
    /** MZIPI */
    private String merchantZip;
    /** CONFIRMI = 'Y' */
    private boolean confirm;

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
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

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getOrigDate() {
        return origDate;
    }

    public void setOrigDate(String origDate) {
        this.origDate = origDate;
    }

    public String getProcDate() {
        return procDate;
    }

    public void setProcDate(String procDate) {
        this.procDate = procDate;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
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

    public boolean isConfirm() {
        return confirm;
    }

    public void setConfirm(boolean confirm) {
        this.confirm = confirm;
    }
}
