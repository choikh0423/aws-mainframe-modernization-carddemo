package com.carddemo.card.dto;

/**
 * The XCTL COCRDLIC performs for a valid selection: the target program, its
 * transaction id, mapset and map (the literals moved into CCARD-NEXT-PROG /
 * CCARD-NEXT-MAPSET / CCARD-NEXT-MAP) and the row's keys moved into
 * CDEMO-ACCT-ID / CDEMO-CARD-NUM (COCRDLIC.cbl:517-569).
 */
public class CardSelectionResponse {

    private final String program;
    private final String tranId;
    private final String mapset;
    private final String map;
    private final String acctId;
    private final String cardNum;

    public CardSelectionResponse(String program, String tranId, String mapset, String map,
                                 String acctId, String cardNum) {
        this.program = program;
        this.tranId = tranId;
        this.mapset = mapset;
        this.map = map;
        this.acctId = acctId;
        this.cardNum = cardNum;
    }

    public String getProgram() { return program; }
    public String getTranId() { return tranId; }
    public String getMapset() { return mapset; }
    public String getMap() { return map; }
    public String getAcctId() { return acctId; }
    public String getCardNum() { return cardNum; }
}
