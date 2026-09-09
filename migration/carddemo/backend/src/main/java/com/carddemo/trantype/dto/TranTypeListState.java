package com.carddemo.trantype.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * The CTLI pseudo-conversational state: {@code WS-THIS-PROGCOMMAREA} of
 * COTRTLIC.cbl (lines 377-419) carried by the client between turns, the way
 * the COMMAREA was carried between CICS tasks.
 *
 * <p>Field names follow the COBOL names so the mapping stays obvious:
 * {@code screenNum} is {@code WS-CA-SCREEN-NUM} (and {@code CA-FIRST-PAGE} is
 * {@code screenNum == 1}), {@code lastPageShown} is
 * {@code CA-LAST-PAGE-SHOWN}, and {@code rows} is
 * {@code WS-CA-SCREEN-ROWS-OUT}, the fetched values the next turn compares its
 * input against.
 */
public class TranTypeListState {

    private String typeFilter = "";
    private String descFilter = "";
    private int screenNum;
    private String firstTypeCode = "";
    private String lastTypeCode = "";
    private boolean nextPageExists;
    private boolean lastPageShown;
    private int rowSelected;
    private boolean deleteRequested;
    private boolean updateRequested;
    private List<TranTypeListRow> rows = new ArrayList<>();

    public TranTypeListState() {
    }

    /** {@code EIBCALEN = 0}: a fresh conversation on page 1 (COTRTLIC.cbl:511-525). */
    public static TranTypeListState firstEntry() {
        TranTypeListState state = new TranTypeListState();
        state.screenNum = 1;
        state.lastPageShown = false;
        return state;
    }

    public TranTypeListState copy() {
        TranTypeListState copy = new TranTypeListState();
        copy.typeFilter = typeFilter;
        copy.descFilter = descFilter;
        copy.screenNum = screenNum;
        copy.firstTypeCode = firstTypeCode;
        copy.lastTypeCode = lastTypeCode;
        copy.nextPageExists = nextPageExists;
        copy.lastPageShown = lastPageShown;
        copy.rowSelected = rowSelected;
        copy.deleteRequested = deleteRequested;
        copy.updateRequested = updateRequested;
        copy.rows = new ArrayList<>(rows);
        return copy;
    }

    /** {@code INITIALIZE WS-CA-PAGING-VARIABLES} (COTRTLIC.cbl:1134, 1172). */
    public void resetPaging() {
        screenNum = 0;
        firstTypeCode = "";
        lastTypeCode = "";
        nextPageExists = false;
        lastPageShown = false;
    }

    public boolean isFirstPage() {
        return screenNum == 1;
    }

    public String getTypeFilter() { return typeFilter; }
    public void setTypeFilter(String typeFilter) { this.typeFilter = typeFilter == null ? "" : typeFilter; }

    public String getDescFilter() { return descFilter; }
    public void setDescFilter(String descFilter) { this.descFilter = descFilter == null ? "" : descFilter; }

    public int getScreenNum() { return screenNum; }
    public void setScreenNum(int screenNum) { this.screenNum = screenNum; }

    public String getFirstTypeCode() { return firstTypeCode; }
    public void setFirstTypeCode(String firstTypeCode) {
        this.firstTypeCode = firstTypeCode == null ? "" : firstTypeCode;
    }

    public String getLastTypeCode() { return lastTypeCode; }
    public void setLastTypeCode(String lastTypeCode) {
        this.lastTypeCode = lastTypeCode == null ? "" : lastTypeCode;
    }

    public boolean isNextPageExists() { return nextPageExists; }
    public void setNextPageExists(boolean nextPageExists) { this.nextPageExists = nextPageExists; }

    public boolean isLastPageShown() { return lastPageShown; }
    public void setLastPageShown(boolean lastPageShown) { this.lastPageShown = lastPageShown; }

    public int getRowSelected() { return rowSelected; }
    public void setRowSelected(int rowSelected) { this.rowSelected = rowSelected; }

    public boolean isDeleteRequested() { return deleteRequested; }
    public void setDeleteRequested(boolean deleteRequested) { this.deleteRequested = deleteRequested; }

    public boolean isUpdateRequested() { return updateRequested; }
    public void setUpdateRequested(boolean updateRequested) { this.updateRequested = updateRequested; }

    public List<TranTypeListRow> getRows() { return rows; }
    public void setRows(List<TranTypeListRow> rows) {
        this.rows = rows == null ? new ArrayList<>() : new ArrayList<>(rows);
    }
}
