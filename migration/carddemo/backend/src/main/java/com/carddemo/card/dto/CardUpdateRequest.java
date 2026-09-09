package com.carddemo.card.dto;

/**
 * A CCUP submit: the search keys, the new field values typed over the screen,
 * and the values fetched when the screen was loaded (CCUP-OLD-DETAILS in the
 * COMMAREA, COCRDUPC.cbl:1343-1369). {@code confirmed} is PF5 - the legacy
 * program only rewrites the record once the changes are validated and the
 * operator confirms (COCRDUPC.cbl:983-1000).
 */
public class CardUpdateRequest {

    private String acctId;
    private String cardNum;
    private String newName;
    private String newStatus;
    private String newExpiryMonth;
    private String newExpiryYear;
    private String oldName;
    private String oldStatus;
    private String oldExpiryMonth;
    private String oldExpiryYear;
    private String oldExpiryDay;
    private Integer oldCvvCd;
    private boolean confirmed;

    public String getAcctId() { return acctId; }
    public void setAcctId(String acctId) { this.acctId = acctId; }

    public String getCardNum() { return cardNum; }
    public void setCardNum(String cardNum) { this.cardNum = cardNum; }

    public String getNewName() { return newName; }
    public void setNewName(String newName) { this.newName = newName; }

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public String getNewExpiryMonth() { return newExpiryMonth; }
    public void setNewExpiryMonth(String newExpiryMonth) { this.newExpiryMonth = newExpiryMonth; }

    public String getNewExpiryYear() { return newExpiryYear; }
    public void setNewExpiryYear(String newExpiryYear) { this.newExpiryYear = newExpiryYear; }

    public String getOldName() { return oldName; }
    public void setOldName(String oldName) { this.oldName = oldName; }

    public String getOldStatus() { return oldStatus; }
    public void setOldStatus(String oldStatus) { this.oldStatus = oldStatus; }

    public String getOldExpiryMonth() { return oldExpiryMonth; }
    public void setOldExpiryMonth(String oldExpiryMonth) { this.oldExpiryMonth = oldExpiryMonth; }

    public String getOldExpiryYear() { return oldExpiryYear; }
    public void setOldExpiryYear(String oldExpiryYear) { this.oldExpiryYear = oldExpiryYear; }

    public String getOldExpiryDay() { return oldExpiryDay; }
    public void setOldExpiryDay(String oldExpiryDay) { this.oldExpiryDay = oldExpiryDay; }

    public Integer getOldCvvCd() { return oldCvvCd; }
    public void setOldCvvCd(Integer oldCvvCd) { this.oldCvvCd = oldCvvCd; }

    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
}
