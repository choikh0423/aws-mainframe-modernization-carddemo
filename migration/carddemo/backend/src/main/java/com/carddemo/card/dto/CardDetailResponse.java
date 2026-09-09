package com.carddemo.card.dto;

/**
 * The CCDL screen after a successful CARDDAT read (COCRDSLC.cbl:457-497).
 * {@code acctId} is the account number as entered - COCRDSLC echoes the input
 * field, not the account on the record (quirk Q-3).
 */
public class CardDetailResponse {

    private final String acctId;
    private final String cardNum;
    private final String embossedName;
    private final String expiryMonth;
    private final String expiryYear;
    private final String activeStatus;
    private final String infoMessage;

    public CardDetailResponse(String acctId, String cardNum, String embossedName, String expiryMonth,
                              String expiryYear, String activeStatus, String infoMessage) {
        this.acctId = acctId;
        this.cardNum = cardNum;
        this.embossedName = embossedName;
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;
        this.activeStatus = activeStatus;
        this.infoMessage = infoMessage;
    }

    public String getAcctId() { return acctId; }
    public String getCardNum() { return cardNum; }
    public String getEmbossedName() { return embossedName; }
    public String getExpiryMonth() { return expiryMonth; }
    public String getExpiryYear() { return expiryYear; }
    public String getActiveStatus() { return activeStatus; }
    public String getInfoMessage() { return infoMessage; }
}
