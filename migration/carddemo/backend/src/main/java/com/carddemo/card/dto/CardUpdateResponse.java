package com.carddemo.card.dto;

import com.carddemo.common.domain.CardRecord;

/**
 * The CCUP screen plus the CCUP-CHANGE-ACTION state the legacy program keeps in
 * its COMMAREA (COCRDUPC.cbl:948-1031). The old-value block is the
 * CCUP-OLD-DETAILS snapshot the next submit must send back so
 * 9300-CHECK-CHANGE-IN-REC can tell whether the record moved underneath.
 */
public class CardUpdateResponse {

    /** CCUP-CHANGE-ACTION values that reach the screen. */
    public enum State {
        /** CCUP-SHOW-DETAILS - details fetched, nothing edited yet. */
        SHOW_DETAILS,
        /** CCUP-NO-CHANGES-DETECTED. */
        NO_CHANGES,
        /** CCUP-CHANGES-OK-NOT-CONFIRMED - edits pass, waiting for PF5. */
        CHANGES_OK_NOT_CONFIRMED,
        /** CCUP-CHANGES-OKAYED-AND-DONE - rewritten. */
        CHANGES_OKAYED_AND_DONE
    }

    private final State state;
    private final String message;
    private final String acctId;
    private final String cardNum;
    private final String embossedName;
    private final String activeStatus;
    private final String expiryMonth;
    private final String expiryYear;
    private final String expiryDay;
    private final Integer cvvCd;

    public CardUpdateResponse(State state, String message, String acctId, String cardNum,
                              String embossedName, String activeStatus, String expiryMonth,
                              String expiryYear, String expiryDay, Integer cvvCd) {
        this.state = state;
        this.message = message;
        this.acctId = acctId;
        this.cardNum = cardNum;
        this.embossedName = embossedName;
        this.activeStatus = activeStatus;
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;
        this.expiryDay = expiryDay;
        this.cvvCd = cvvCd;
    }

    /**
     * Builds the screen from a CARDDAT record, slicing CARD-EXPIRAION-DATE the
     * way COCRDUPC does: (1:4) year, (6:2) month, (9:2) day
     * (COCRDUPC.cbl:1361-1366). The embossed name is upper-cased on the way in,
     * exactly as the legacy INSPECT CONVERTING does (COCRDUPC.cbl:1356-1358).
     */
    public static CardUpdateResponse fromRecord(State state, String message, String acctId, CardRecord record) {
        String expiry = record.getExpiraionDate();
        return new CardUpdateResponse(state, message, acctId, record.getCardNum(),
                record.getEmbossedName().toUpperCase(), record.getActiveStatus(),
                expiry.substring(5, 7), expiry.substring(0, 4), expiry.substring(8, 10),
                record.getCvvCd());
    }

    public State getState() { return state; }
    public String getMessage() { return message; }
    public String getAcctId() { return acctId; }
    public String getCardNum() { return cardNum; }
    public String getEmbossedName() { return embossedName; }
    public String getActiveStatus() { return activeStatus; }
    public String getExpiryMonth() { return expiryMonth; }
    public String getExpiryYear() { return expiryYear; }
    public String getExpiryDay() { return expiryDay; }
    public Integer getCvvCd() { return cvvCd; }
}
