package com.carddemo.card.message;

/**
 * S-03 CardManagement screen literals, reproduced verbatim (including spacing)
 * from the COBOL sources so the migrated screens show exactly what the 3270
 * maps showed. Nothing here may be reworded.
 */
public final class CardManagementMessages {

    private CardManagementMessages() {
    }

    // ---- CCLI / COCRDLIC ------------------------------------------------

    /** COCRDLIC.cbl:124. */
    public static final String MORE_THAN_ONE_ACTION = "PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE";
    /** COCRDLIC.cbl:126. */
    public static final String INVALID_ACTION_CODE = "INVALID ACTION CODE";
    /** COCRDLIC.cbl:908. */
    public static final String NO_MORE_PAGES = "NO MORE PAGES TO DISPLAY";
    /** COCRDLIC.cbl:1219, 1239. */
    public static final String NO_MORE_RECORDS = "NO MORE RECORDS TO SHOW";
    /** COCRDLIC.cbl:903. */
    public static final String NO_PREVIOUS_PAGES = "NO PREVIOUS PAGES TO DISPLAY";
    /** COCRDLIC.cbl:116. */
    public static final String INFORM_REC_ACTIONS = "TYPE S FOR DETAIL, U TO UPDATE ANY RECORD";

    // ---- Search-key edits, shared by CCLI / CCDL / CCUP -----------------

    /** COCRDLIC.cbl:1022, COCRDSLC.cbl:669, COCRDUPC.cbl:745. */
    public static final String ACCOUNT_FILTER_NOT_NUMERIC =
            "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER";
    /** COCRDLIC.cbl:1058, COCRDSLC.cbl:710, COCRDUPC.cbl:789. */
    public static final String CARD_FILTER_NOT_NUMERIC =
            "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER";

    // ---- CCDL / COCRDSLC ------------------------------------------------

    /** COCRDSLC.cbl:130 - the three leading spaces are part of the literal. */
    public static final String DISPLAYING_REQUESTED_DETAILS = "   Displaying requested details";
    /** COCRDSLC.cbl:139, COCRDUPC.cbl:178. */
    public static final String ACCOUNT_NOT_PROVIDED = "Account number not provided";
    /** COCRDSLC.cbl:141, COCRDUPC.cbl:180. */
    public static final String CARD_NOT_PROVIDED = "Card number not provided";
    /** COCRDSLC.cbl:143, COCRDUPC.cbl:186. */
    public static final String NO_INPUT_RECEIVED = "No input received";
    /** COCRDSLC.cbl:154, COCRDUPC.cbl:204. */
    public static final String DID_NOT_FIND_ACCTCARD_COMBO = "Did not find cards for this search condition";

    // ---- CCUP / COCRDUPC ------------------------------------------------

    /** COCRDUPC.cbl:161. */
    public static final String SHOW_DETAILS = "Details of selected card shown above";
    /** COCRDUPC.cbl:167. */
    public static final String CHANGES_VALIDATED = "Changes validated.Press F5 to save";
    /** COCRDUPC.cbl:169. */
    public static final String CHANGES_COMMITTED = "Changes committed to database";
    /** COCRDUPC.cbl:188. */
    public static final String NO_CHANGES_DETECTED = "No change detected with respect to values fetched.";
    /** COCRDUPC.cbl:182. */
    public static final String CARD_NAME_NOT_PROVIDED = "Card name not provided";
    /** COCRDUPC.cbl:184. */
    public static final String CARD_NAME_NOT_ALPHA = "Card name can only contain alphabets and spaces";
    /** COCRDUPC.cbl:196. */
    public static final String CARD_STATUS_NOT_YES_NO = "Card Active Status must be Y or N";
    /** COCRDUPC.cbl:198. */
    public static final String CARD_EXPIRY_MONTH_INVALID = "Card expiry month must be between 1 and 12";
    /** COCRDUPC.cbl:200. */
    public static final String CARD_EXPIRY_YEAR_INVALID = "Invalid card expiry year";
    /** COCRDUPC.cbl:206. */
    public static final String COULD_NOT_LOCK = "Could not lock record for update";
    /** COCRDUPC.cbl:208. */
    public static final String RECORD_CHANGED_BY_OTHER = "Record changed by some one else. Please review";
    /** COCRDUPC.cbl:210. */
    public static final String UPDATE_FAILED = "Update of record failed";
}
