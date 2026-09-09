package com.carddemo.account.util;

/**
 * Every message string CAVW and CAUP can put on the screen, verbatim from the COBOL literals
 * (double spaces, missing spaces before punctuation and all).
 */
public final class AccountMessages {

    /** COACTVWC:110-111 — empty view screen. */
    public static final String VIEW_PROMPT = "Enter or update id of account to display";
    /** COACTVWC:112-113 — details displayed. */
    public static final String VIEW_DETAILS_SHOWN = "Displaying details of given Account";
    /** COACTVWC:669-673 — non-numeric or zero account filter. */
    public static final String VIEW_INVALID_ACCOUNT_FILTER =
            "Account Filter must  be a non-zero 11 digit number";

    /** COACTUPC:2960-2961 — empty update screen. */
    public static final String UPDATE_PROMPT = "Enter or update id of account to update";
    /** COACTUPC:2962-2963 — details fetched and enterable. */
    public static final String UPDATE_DETAILS_SHOWN = "Update account details presented above.";
    /** COACTUPC:2966-2967 — every edit passed. */
    public static final String UPDATE_CHANGES_VALIDATED = "Changes validated.Press F5 to save";
    /** COACTUPC:2968-2970 — rewrite succeeded. */
    public static final String UPDATE_CHANGES_COMMITTED = "Changes committed to database";
    /** COACTUPC:2971-2974 — lock or rewrite failed. */
    public static final String UPDATE_CHANGES_UNSUCCESSFUL = "Changes unsuccessful. Please try again";

    /** COACTVWC:600-620, COACTUPC:1441-1443 — blank account id (quirk FR-AQ-01). */
    public static final String NO_INPUT_RECEIVED = "No input received";
    /** COACTUPC:1806-1810 — non-numeric or zero account id on the update screen. */
    public static final String UPDATE_INVALID_ACCOUNT_FILTER =
            "Account Number if supplied must be a 11 digit Non-Zero Number";
    /** COACTUPC:1462-1466 — ENTER with nothing changed. */
    public static final String NO_CHANGE_DETECTED = "No change detected with respect to values fetched.";

    /** COACTUPC:3908-3912 — READ UPDATE on ACCTDAT failed. */
    public static final String COULD_NOT_LOCK_ACCOUNT = "Could not lock account record for update";
    /** COACTUPC:3936-3940 — READ UPDATE on CUSTDAT failed. */
    public static final String COULD_NOT_LOCK_CUSTOMER = "Could not lock customer record for update";
    /** COACTUPC:2632-2636 — the locked rows no longer match the fetched snapshot. */
    public static final String RECORD_CHANGED_BY_SOMEONE_ELSE =
            "Record changed by some one else. Please review";
    /** COACTUPC:2643-2646 — a REWRITE failed; the unit of work is rolled back. */
    public static final String UPDATE_OF_RECORD_FAILED = "Update of record failed";

    private static final int RETURN_MSG_LENGTH = 75;

    private AccountMessages() {
    }

    /**
     * {@code 'Account:' <id> ' not found in' ' Cross ref file.  Resp:' <resp> ' Reas:' <reas>}
     * (COACTVWC:740-756). {@code RESP}/{@code RESP2} are binary fields moved into
     * {@code PIC X(10)}, so they render as nine digits followed by a space; the whole message is
     * then truncated into {@code WS-RETURN-MSG PIC X(75)}.
     */
    public static String xrefNotFound(String accountId11, int resp, int reason) {
        return truncate("Account:" + accountId11 + " not found in" + " Cross ref file.  Resp:"
                + respField(resp) + " Reas:" + respField(reason));
    }

    /** COACTVWC:791-805. */
    public static String accountNotFound(String accountId11, int resp, int reason) {
        return truncate("Account:" + accountId11 + " not found in" + " Acct Master file.Resp:"
                + respField(resp) + " Reas:" + respField(reason));
    }

    /** COACTVWC:842-857. */
    public static String customerNotFound(String customerId9, int resp, int reason) {
        return truncate("CustId:" + customerId9 + " not found" + " in customer master.Resp: "
                + respField(resp) + " REAS:" + respField(reason));
    }

    private static String respField(int value) {
        return String.format("%09d ", value);
    }

    private static String truncate(String message) {
        String clipped = message.length() > RETURN_MSG_LENGTH
                ? message.substring(0, RETURN_MSG_LENGTH)
                : message;
        return trimTrailing(clipped);
    }

    private static String trimTrailing(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(0, end);
    }
}
