package com.carddemo.common.message;

/**
 * The screen-message literals shared by every CardDemo program, reproduced
 * verbatim from the COBOL sources. Streams must reuse these constants rather
 * than retype the text, so every migrated screen shows exactly what the 3270
 * map showed.
 *
 * <p>The COBOL fields are PIC X(50), so the literals are space-padded on the
 * mainframe; the constants here carry the trimmed text that actually reaches
 * the operator.
 */
public final class CardDemoMessages {

    private CardDemoMessages() {
    }

    /** CCDA-MSG-THANK-YOU (app/cpy/CSMSG01Y.cpy). */
    public static final String THANK_YOU = "Thank you for using CardDemo application...";
    /** CCDA-MSG-INVALID-KEY (app/cpy/CSMSG01Y.cpy). */
    public static final String INVALID_KEY = "Invalid key pressed. Please see below...";

    /** COSGN00C.cbl:120 - empty User ID. */
    public static final String SIGNON_ENTER_USER_ID = "Please enter User ID ...";
    /** COSGN00C.cbl:125 - empty Password. */
    public static final String SIGNON_ENTER_PASSWORD = "Please enter Password ...";
    /** COSGN00C.cbl:242 - USRSEC record found, password mismatch. */
    public static final String SIGNON_WRONG_PASSWORD = "Wrong Password. Try again ...";
    /** COSGN00C.cbl:249 - USRSEC read returned NOTFND. */
    public static final String SIGNON_USER_NOT_FOUND = "User not found. Try again ...";
    /** COSGN00C.cbl:254 - USRSEC read failed for any other reason. */
    public static final String SIGNON_UNABLE_TO_VERIFY = "Unable to verify the User ...";

    /** COMEN01C.cbl / COADM01C.cbl - option outside the valid range. */
    public static final String MENU_INVALID_OPTION = "Please enter a valid option number...";
    /** COMEN01C.cbl - a 'U' user selected an admin-only option. */
    public static final String MENU_ADMIN_ONLY = "No access - Admin Only option... ";

    /**
     * COMEN01C.cbl:163-167 - the selected option maps to a program that is not in
     * the estate. The COBOL STRING delimits the option name by two spaces.
     */
    public static String menuOptionNotInstalled(String optionName) {
        return "This option " + delimitedBy(optionName, "  ") + " is not installed...";
    }

    /**
     * COMEN01C.cbl:172-176 - the selected option maps to a DUMMY program. The
     * COBOL STRING delimits the option name by a single space, so only the first
     * word of the name reaches the message.
     */
    public static String menuOptionComingSoon(String optionName) {
        return "This option " + delimitedBy(optionName, " ") + "is coming soon ...";
    }

    /** COBOL {@code STRING ... DELIMITED BY <literal>}: take everything before it. */
    private static String delimitedBy(String value, String delimiter) {
        int end = value.indexOf(delimiter);
        return end < 0 ? value : value.substring(0, end);
    }
}
