package com.carddemo.trantype.dto;

/**
 * The CTTU pseudo-conversational state: {@code WS-THIS-PROGCOMMAREA} of
 * COTRTUPC.cbl (lines 294-336).
 *
 * <p>{@code changeAction} is {@code TTUP-CHANGE-ACTION}, the single character
 * that drives the whole screen; its values are the constants below. The old
 * details are what the SELECT returned, the new details are what the operator
 * typed — the comparison between the two decides whether there is anything to
 * save ({@code 1205-COMPARE-OLD-NEW}).
 */
public class TranTypeUpdateState {

    /** LOW-VALUES — {@code TTUP-DETAILS-NOT-FETCHED}. */
    public static final String NOT_FETCHED = " ";
    /** 'K' — {@code TTUP-INVALID-SEARCH-KEYS}. */
    public static final String INVALID_SEARCH_KEYS = "K";
    /** 'X' — {@code TTUP-DETAILS-NOT-FOUND}. */
    public static final String DETAILS_NOT_FOUND = "X";
    /** 'S' — {@code TTUP-SHOW-DETAILS}. */
    public static final String SHOW_DETAILS = "S";
    /** 'R' — {@code TTUP-CREATE-NEW-RECORD}. */
    public static final String CREATE_NEW_RECORD = "R";
    /** '9' — {@code TTUP-CONFIRM-DELETE}. */
    public static final String CONFIRM_DELETE = "9";
    /** '8' — {@code TTUP-START-DELETE}. */
    public static final String START_DELETE = "8";
    /** '7' — {@code TTUP-DELETE-DONE}. */
    public static final String DELETE_DONE = "7";
    /** '6' — {@code TTUP-DELETE-FAILED}. */
    public static final String DELETE_FAILED = "6";
    /** 'E' — {@code TTUP-CHANGES-NOT-OK}. */
    public static final String CHANGES_NOT_OK = "E";
    /** 'N' — {@code TTUP-CHANGES-OK-NOT-CONFIRMED}. */
    public static final String CHANGES_OK_NOT_CONFIRMED = "N";
    /** 'L' — {@code TTUP-CHANGES-OKAYED-LOCK-ERROR}. */
    public static final String CHANGES_LOCK_ERROR = "L";
    /** 'F' — {@code TTUP-CHANGES-OKAYED-BUT-FAILED}. */
    public static final String CHANGES_FAILED = "F";
    /** 'C' — {@code TTUP-CHANGES-OKAYED-AND-DONE}. */
    public static final String CHANGES_DONE = "C";
    /** 'B' — {@code TTUP-CHANGES-BACKED-OUT}. */
    public static final String CHANGES_BACKED_OUT = "B";

    private String changeAction = NOT_FETCHED;
    private String oldTypeCode = "";
    private String oldDescription = "";
    private String newTypeCode = "";
    private String newDescription = "";
    private boolean programReenter;

    public TranTypeUpdateState() {
    }

    /** {@code EIBCALEN = 0} or arrival from COADM01C / COTRTLIC. */
    public static TranTypeUpdateState firstEntry() {
        return new TranTypeUpdateState();
    }

    public TranTypeUpdateState copy() {
        TranTypeUpdateState copy = new TranTypeUpdateState();
        copy.changeAction = changeAction;
        copy.oldTypeCode = oldTypeCode;
        copy.oldDescription = oldDescription;
        copy.newTypeCode = newTypeCode;
        copy.newDescription = newDescription;
        copy.programReenter = programReenter;
        return copy;
    }

    public boolean isState(String... states) {
        for (String state : states) {
            if (state.equals(changeAction)) {
                return true;
            }
        }
        return false;
    }

    /** {@code TTUP-DELETE-IN-PROGRESS}: '9', '8', '7' or '6'. */
    public boolean isDeleteInProgress() {
        return isState(CONFIRM_DELETE, START_DELETE, DELETE_DONE, DELETE_FAILED);
    }

    /** {@code TTUP-CHANGES-MADE}: 'E', 'N', 'L' or 'F'. */
    public boolean isChangesMade() {
        return isState(CHANGES_NOT_OK, CHANGES_OK_NOT_CONFIRMED, CHANGES_LOCK_ERROR, CHANGES_FAILED);
    }

    /** {@code TTUP-CHANGES-FAILED}: 'L' or 'F'. */
    public boolean isChangesFailed() {
        return isState(CHANGES_LOCK_ERROR, CHANGES_FAILED);
    }

    /** {@code TTUP-OLD-DETAILS EQUAL LOW-VALUES OR SPACES}. */
    public boolean isOldDetailsEmpty() {
        return oldTypeCode.isBlank() && oldDescription.isBlank();
    }

    public String getChangeAction() { return changeAction; }
    public void setChangeAction(String changeAction) {
        this.changeAction = changeAction == null ? NOT_FETCHED : changeAction;
    }

    public String getOldTypeCode() { return oldTypeCode; }
    public void setOldTypeCode(String oldTypeCode) {
        this.oldTypeCode = oldTypeCode == null ? "" : oldTypeCode;
    }

    public String getOldDescription() { return oldDescription; }
    public void setOldDescription(String oldDescription) {
        this.oldDescription = oldDescription == null ? "" : oldDescription;
    }

    public String getNewTypeCode() { return newTypeCode; }
    public void setNewTypeCode(String newTypeCode) {
        this.newTypeCode = newTypeCode == null ? "" : newTypeCode;
    }

    public String getNewDescription() { return newDescription; }
    public void setNewDescription(String newDescription) {
        this.newDescription = newDescription == null ? "" : newDescription;
    }

    public boolean isProgramReenter() { return programReenter; }
    public void setProgramReenter(boolean programReenter) { this.programReenter = programReenter; }
}
