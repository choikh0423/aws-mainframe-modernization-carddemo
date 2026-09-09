package com.carddemo.trantype.message;

/**
 * Every message string S-08 can put on the screen, verbatim from the COBOL
 * literals of {@code COTRTLIC.cbl} and {@code COTRTUPC.cbl}.
 *
 * <p>Spacing, missing spaces and inconsistent punctuation are part of the text
 * and are reproduced exactly (for example {@code "HIGHLIGHTED row deleted.Hit
 * Enter to continue"} and the trailing space of {@code "Record not found.
 * Deleted by others ? "}).
 */
public final class TranTypeMessages {

    private TranTypeMessages() {
    }

    // ---------------------------------------------------------------- CTLI
    // COTRTLIC.cbl:239-248 - WS-INFO-MSG 88 levels (the centred INFOMSG line).

    /** COTRTLIC.cbl:239-240 - the default list info line. */
    public static final String LIST_INFO_REC_ACTIONS = "Type U to update, D to delete any record";
    /** COTRTLIC.cbl:241-242 - a `D` row is pending confirmation. */
    public static final String LIST_INFO_DELETE = "Delete HIGHLIGHTED row ? Press F10 to confirm";
    /** COTRTLIC.cbl:243-244 - a `U` row is pending confirmation. */
    public static final String LIST_INFO_UPDATE = "Update HIGHLIGHTED row. Press F10 to save";
    /** COTRTLIC.cbl:245-246 - the row was deleted. */
    public static final String LIST_INFO_DELETE_SUCCESS = "HIGHLIGHTED row deleted.Hit Enter to continue";
    /** COTRTLIC.cbl:247-248 - the row was updated. */
    public static final String LIST_INFO_UPDATE_SUCCESS = "HIGHLIGHTED row was updated";

    // COTRTLIC.cbl:249-262 - WS-RETURN-MSG 88 levels (the ERRMSG line).

    /** COTRTLIC.cbl:253-254 - the browse found nothing at all. */
    public static final String LIST_NO_RECORDS_FOUND = "No records found for this search condition.";
    /** COTRTLIC.cbl:255-256 - F8 past the last page of a filtered browse. */
    public static final String LIST_NO_MORE_RECORDS = "No more pages for these search conditions";
    /** COTRTLIC.cbl:257-258 - more than one row flagged. */
    public static final String LIST_MORE_THAN_1_ACTION = "Please select only 1 action";
    /** COTRTLIC.cbl:259-260 - a row flag other than blank/U/D. */
    public static final String LIST_INVALID_ACTION_CODE = "Action code selected is invalid";
    /** COTRTLIC.cbl:261-262 - F10 with the fetched description unchanged. */
    public static final String LIST_NO_CHANGES_DETECTED = "No change detected with respect to database values.";
    /** COTRTLIC.cbl:1111-1117 - non-numeric type filter. */
    public static final String LIST_TYPE_FILTER_INVALID =
            "TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER";
    /** COTRTLIC.cbl:1263-1265 - the filters match no row. */
    public static final String LIST_NO_RECORDS_FOR_FILTERS = "No Records found for these filter conditions";
    /** COTRTLIC.cbl:1534-1535 - F7 on page 1. */
    public static final String LIST_NO_PREVIOUS_PAGES = "No previous pages to display";
    /** COTRTLIC.cbl:1539-1540 - F8 repeated on the last page. */
    public static final String LIST_NO_MORE_PAGES = "No more pages to display";
    /** COTRTLIC.cbl:1861-1864 - UPDATE returned SQLCODE +100. */
    public static final String LIST_UPDATE_NOT_FOUND = "Record not found. Deleted by others ? ";
    /** COTRTLIC.cbl:1870-1873 - UPDATE returned SQLCODE -911. */
    public static final String LIST_UPDATE_DEADLOCK = "Deadlock. Someone else updating ?";
    /** COTRTLIC.cbl:1917-1921 - DELETE refused by the category foreign key (-532). */
    public static final String LIST_DELETE_HAS_CHILDREN = "Please delete associated child records first:";
    /** COTRTLIC.cbl:1927-1930 - any other DELETE failure. */
    public static final String LIST_DELETE_FAILED = "Delete failed with message:";

    // ---------------------------------------------------------------- CTTU
    // COTRTUPC.cbl:1213-1241 - the info line literals.

    /** COTRTUPC.cbl:1213-1214 - no key entered yet. */
    public static final String UPD_INFO_ENTER_KEY = "Enter transaction type to be maintained";
    /** COTRTUPC.cbl:1215-1216 - the record was fetched. */
    public static final String UPD_INFO_DETAILS_SHOWN = "Selected transaction type shown above";
    /** COTRTUPC.cbl:1219-1220 - the key does not exist. */
    public static final String UPD_INFO_PRESS_F5_TO_ADD = "Press F05 to add. F12 to cancel";
    /** COTRTUPC.cbl:1225-1226 - F4 pressed once. */
    public static final String UPD_INFO_CONFIRM_DELETE = "Delete this record ? Press F4 to confirm";
    /** COTRTUPC.cbl:1231-1232 - the row was deleted. */
    public static final String UPD_INFO_DELETE_SUCCESS = "Delete successful.";
    /** COTRTUPC.cbl:1229-1230 - the record is being changed. */
    public static final String UPD_INFO_UPDATE_DETAILS = "Update transaction type details shown.";
    /** COTRTUPC.cbl:1233-1234 - add mode. */
    public static final String UPD_INFO_ENTER_NEW_DETAILS = "Enter new transaction type details.";
    /** COTRTUPC.cbl:1237-1238 - the edits passed. */
    public static final String UPD_INFO_CHANGES_VALIDATED = "Changes validated.Press F5 to save";
    /** COTRTUPC.cbl:1240-1241 - the write committed. */
    public static final String UPD_INFO_CHANGES_COMMITTED = "Changes committed to database";
    /** COTRTUPC.cbl:1243-1244 - the write failed. */
    public static final String UPD_INFO_CHANGES_UNSUCCESSFUL = "Changes unsuccessful";

    // COTRTUPC.cbl - the error-line literals.

    /** COTRTUPC.cbl:436 - F3 from the maintenance screen. */
    public static final String UPD_EXIT = "PF03 pressed.Exiting";
    /** COTRTUPC.cbl:604 - a key that is not valid in the current state. */
    public static final String UPD_INVALID_KEY = "Invalid key pressed";
    /** COTRTUPC.cbl:723 - ENTER with an empty screen. */
    public static final String UPD_NO_INPUT = "No input received";
    /** COTRTUPC.cbl:1496 - the key is not in the table. */
    public static final String UPD_NOT_FOUND = "No record found for this key in database";
    /** COTRTUPC.cbl:800-801 - nothing changed since the fetch. */
    public static final String UPD_NO_CHANGE_DETECTED = "No change detected with respect to values fetched.";
    /** COTRTUPC.cbl:1006 - F12 while a delete is pending. */
    public static final String UPD_DELETE_CANCELLED = "Delete was cancelled";
    /** COTRTUPC.cbl:1042 - F12 while changes are pending. */
    public static final String UPD_UPDATE_CANCELLED = "Update was cancelled";
    /** COTRTUPC.cbl:1568-1570 - the row could not be locked. */
    public static final String UPD_COULD_NOT_LOCK = "Could not lock record for update";
    /** COTRTUPC.cbl:1580-1582 - the row changed under us. */
    public static final String UPD_CHANGED_BY_OTHERS = "Record changed by some one else. Please review";
    /** COTRTUPC.cbl:1600-1602 - the UPDATE/INSERT failed. */
    public static final String UPD_UPDATE_FAILED = "Update of record failed";
    /**
     * COTRTUPC.cbl:1651-1661 - the DELETE failed. {@code RECORD-DELETE-FAILED}
     * moves {@code 'Delete of record failed'} into WS-RETURN-MSG, but the
     * following STRING overwrites it, so this literal is what the screen shows.
     */
    public static final String UPD_DELETE_FAILED = "Delete failed with message:";
    /** COTRTUPC.cbl:1643-1646 - DELETE refused by the category foreign key (-532). */
    public static final String UPD_DELETE_HAS_CHILDREN = "Please delete associated child records first:";

    // Field edits shared by both screens (COTRTLIC 1240-EDIT-ALPHANUM-REQD /
    // COTRTUPC 1300-EDIT-ALPHANUM-REQD build these with STRING).

    /** {@code 'Tran Type code' + ' must be supplied.'}. */
    public static final String TYPE_CODE_REQUIRED = "Tran Type code must be supplied.";
    /** {@code 'Tran Type code' + ' must be numeric.'}. */
    public static final String TYPE_CODE_NOT_NUMERIC = "Tran Type code must be numeric.";
    /** {@code 'Tran Type code' + ' must not be zero.'}. */
    public static final String TYPE_CODE_ZERO = "Tran Type code must not be zero.";
    /** {@code 'Transaction Desc' + ' must be supplied.'}. */
    public static final String DESCRIPTION_REQUIRED = "Transaction Desc must be supplied.";
    /** {@code 'Transaction Desc' + ' can have numbers or alphabets only.'}. */
    public static final String DESCRIPTION_NOT_ALPHANUM = "Transaction Desc can have numbers or alphabets only.";

    // Batch - COBTUPDT.cbl DISPLAY literals.

    /** COBTUPDT.cbl:112. */
    public static final String BATCH_ADDING = "ADDING RECORD";
    /** COBTUPDT.cbl:115. */
    public static final String BATCH_UPDATING = "UPDATING RECORD";
    /** COBTUPDT.cbl:118. */
    public static final String BATCH_DELETING = "DELETING RECORD";
    /** COBTUPDT.cbl:121. */
    public static final String BATCH_IGNORING_COMMENT = "IGNORING COMMENTED LINE";
    /** COBTUPDT.cbl:145. */
    public static final String BATCH_INSERTED = "RECORD INSERTED SUCCESSFULLY";
    /** COBTUPDT.cbl:176. */
    public static final String BATCH_UPDATED = "RECORD UPDATED SUCCESSFULLY";
    /** COBTUPDT.cbl:206. */
    public static final String BATCH_DELETED = "RECORD DELETED SUCCESSFULLY";
    /** COBTUPDT.cbl:181, 211 - SQLCODE +100 on UPDATE/DELETE. */
    public static final String BATCH_NO_RECORDS = "No records found.";
    /** COBTUPDT.cbl:124-127 - the operation code is not A/U/D/*. */
    public static final String BATCH_TYPE_NOT_VALID = "ERROR: TYPE NOT VALID";
    /** COBTUPDT.cbl:149-155, 189-191, 220-222 - prefix of the SQL failure text. */
    public static final String BATCH_SQL_ERROR_PREFIX = "Error accessing: TRANSACTION_TYPE table. SQLCODE:";

    /**
     * COBTUPDT.cbl:155-161: the SQLCODE is moved through
     * {@code WS-VAR-SQLCODE PIC ----9} before it is STRINGed, so it is
     * right-justified in five characters with the sign next to the digits.
     */
    public static String batchSqlError(int sqlCode) {
        return BATCH_SQL_ERROR_PREFIX + String.format("%5d", sqlCode);
    }

    /**
     * {@code 9999-FORMAT-DB2-MESSAGE} (cpy/CSDB2RPY.cpy:2057-2077) builds
     * {@code TRIM(action) + ' SQLCODE:' + sqlcode + ' ' + DSNTIAC text}. Only
     * the action and the SQLCODE survive boundary B-08 — there is no DSNTIAC
     * once the embedded SQL is JPA — so the migrated screens emit the same
     * prefix with the mapped code.
     */
    public static String withSqlCode(String action, int sqlCode) {
        return action.trim() + " SQLCODE:" + sqlCode;
    }
}
