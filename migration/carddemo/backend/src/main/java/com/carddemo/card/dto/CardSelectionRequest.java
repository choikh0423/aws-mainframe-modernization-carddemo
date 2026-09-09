package com.carddemo.card.dto;

import java.util.List;

/**
 * The seven CRDSELn flags plus the account/card values displayed on the seven
 * rows, i.e. exactly what COCRDLIC's 2100-RECEIVE-SCREEN reads into
 * WS-EDIT-SELECT(1..7) and what it already holds in WS-ROW-ACCTNO /
 * WS-ROW-CARD-NUM (COCRDLIC.cbl:962-978).
 */
public class CardSelectionRequest {

    private List<String> flags;
    private List<CardListRow> rows;

    public List<String> getFlags() { return flags; }
    public void setFlags(List<String> flags) { this.flags = flags; }

    public List<CardListRow> getRows() { return rows; }
    public void setRows(List<CardListRow> rows) { this.rows = rows; }
}
