package com.carddemo.user;

/**
 * Every screen literal S-05 UserManagement can display, reproduced character for
 * character from the COBOL sources (see
 * docs/migration/streams/UserManagement/UserManagement_functional_requirement.md,
 * FR-US-3). Shared literals such as CCDA-MSG-INVALID-KEY stay in
 * {@code com.carddemo.common.message.CardDemoMessages}.
 */
public final class UserMessages {

    private UserMessages() {
    }

    // --- required-empty edits (COUSR01C.cbl:115-152, COUSR02C.cbl:177-213,
    //     COUSR03C.cbl:142-154) ---
    public static final String FIRST_NAME_EMPTY = "First Name can NOT be empty...";
    public static final String LAST_NAME_EMPTY = "Last Name can NOT be empty...";
    public static final String USER_ID_EMPTY = "User ID can NOT be empty...";
    public static final String PASSWORD_EMPTY = "Password can NOT be empty...";
    public static final String USER_TYPE_EMPTY = "User Type can NOT be empty...";

    // --- CU01 add outcomes (COUSR01C.cbl:250-272) ---
    public static final String USER_ID_ALREADY_EXIST = "User ID already exist...";
    public static final String UNABLE_TO_ADD_USER = "Unable to Add User...";

    // --- CU02/CU03 lookup outcomes (COUSR02C.cbl:334-352, COUSR03C.cbl:281-299) ---
    public static final String USER_ID_NOT_FOUND = "User ID NOT found...";
    public static final String UNABLE_TO_LOOKUP_USER = "Unable to lookup User...";
    public static final String PRESS_PF5_TO_SAVE = "Press PF5 key to save your updates ...";
    public static final String PRESS_PF5_TO_DELETE = "Press PF5 key to delete this user ...";

    // --- CU02 update outcomes (COUSR02C.cbl:238-243, 368-389) ---
    public static final String PLEASE_MODIFY_TO_UPDATE = "Please modify to update ...";
    /**
     * COUSR03C reuses this update literal for a failed DELETE (COUSR03C.cbl:330-334)
     * — quirk Q3, preserved.
     */
    public static final String UNABLE_TO_UPDATE_USER = "Unable to Update User...";

    // --- CU00 browse outcomes (COUSR00C.cbl:214-276, 600-681) ---
    public static final String INVALID_SELECTION = "Invalid selection. Valid values are U and D";
    public static final String ALREADY_AT_TOP = "You are already at the top of the page...";
    public static final String ALREADY_AT_BOTTOM = "You are already at the bottom of the page...";
    public static final String AT_TOP_OF_PAGE = "You are at the top of the page...";
    public static final String REACHED_BOTTOM = "You have reached the bottom of the page...";
    public static final String REACHED_TOP = "You have reached the top of the page...";

    /** COUSR01C.cbl:250-259 — {@code STRING 'User ' SEC-USR-ID DELIMITED BY SPACE ' has been added ...'}. */
    public static String userAdded(String userId) {
        return "User " + delimitedBySpace(userId) + " has been added ...";
    }

    /** COUSR02C.cbl:368-376. */
    public static String userUpdated(String userId) {
        return "User " + delimitedBySpace(userId) + " has been updated ...";
    }

    /** COUSR03C.cbl:313-322. */
    public static String userDeleted(String userId) {
        return "User " + delimitedBySpace(userId) + " has been deleted ...";
    }

    /**
     * COBOL {@code STRING ... DELIMITED BY SPACE}: only the text before the first
     * blank reaches the message (quirk Q12).
     */
    private static String delimitedBySpace(String value) {
        String v = value == null ? "" : value;
        int end = v.indexOf(' ');
        return end < 0 ? v : v.substring(0, end);
    }
}
