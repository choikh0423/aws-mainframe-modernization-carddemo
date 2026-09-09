package com.carddemo.card.dto;

import com.carddemo.common.domain.CardRecord;

/**
 * One CCLI detail line: the account number, card number and active flag
 * COCRDLIC moves into WS-ROW-ACCTNO / WS-ROW-CARD-NUM / WS-ROW-CARD-STATUS
 * (COCRDLIC.cbl:1165-1171). The account number is rendered 11 digits
 * zero-padded, matching PIC 9(11) on the map.
 */
public class CardListRow {

    private final String acctId;
    private final String cardNum;
    private final String activeStatus;

    public CardListRow(String acctId, String cardNum, String activeStatus) {
        this.acctId = acctId;
        this.cardNum = cardNum;
        this.activeStatus = activeStatus;
    }

    public static CardListRow from(CardRecord record) {
        return new CardListRow(String.format("%011d", record.getAcctId()),
                record.getCardNum(), record.getActiveStatus());
    }

    public String getAcctId() { return acctId; }
    public String getCardNum() { return cardNum; }
    public String getActiveStatus() { return activeStatus; }
}
