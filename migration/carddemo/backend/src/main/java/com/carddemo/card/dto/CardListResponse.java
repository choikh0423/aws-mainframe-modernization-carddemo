package com.carddemo.card.dto;

import java.util.List;

/**
 * One CCLI page plus the paging state COCRDLIC kept in its private COMMAREA
 * (WS-THIS-PROGCOMMAREA: WS-CA-SCREEN-NUM, WS-CA-FIRST-CARDKEY,
 * WS-CA-LAST-CARDKEY, CA-NEXT-PAGE-*, CA-LAST-PAGE-*). The CICS
 * pseudo-conversation is stateless here: the client echoes this state back on
 * the next PF7/PF8 request.
 */
public class CardListResponse {

    private final List<CardListRow> rows;
    private final int pageNumber;
    private final String firstCardNum;
    private final String lastCardNum;
    private final boolean nextPageExists;
    private final boolean lastPageShown;
    private final String infoMessage;
    private final String errorMessage;

    public CardListResponse(List<CardListRow> rows, int pageNumber, String firstCardNum, String lastCardNum,
                            boolean nextPageExists, boolean lastPageShown,
                            String infoMessage, String errorMessage) {
        this.rows = rows;
        this.pageNumber = pageNumber;
        this.firstCardNum = firstCardNum;
        this.lastCardNum = lastCardNum;
        this.nextPageExists = nextPageExists;
        this.lastPageShown = lastPageShown;
        this.infoMessage = infoMessage;
        this.errorMessage = errorMessage;
    }

    public List<CardListRow> getRows() { return rows; }
    public int getPageNumber() { return pageNumber; }
    public String getFirstCardNum() { return firstCardNum; }
    public String getLastCardNum() { return lastCardNum; }
    public boolean isNextPageExists() { return nextPageExists; }
    public boolean isLastPageShown() { return lastPageShown; }
    public String getInfoMessage() { return infoMessage; }
    public String getErrorMessage() { return errorMessage; }
    public int getCount() { return rows.size(); }
}
