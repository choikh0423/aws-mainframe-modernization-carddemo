package com.carddemo.pendingauth.service;

import com.carddemo.pendingauth.util.PendingAuthFormat;

/**
 * The literal message text of the PendingAuthorizations programs. Every string
 * here is copied character for character from the COBOL source; the screens are
 * the sign-off oracle, so nothing may be reworded.
 */
public final class PendingAuthMessages {

    /** COPAUS0C.cbl:269. */
    public static final String ENTER_ACCT_ID = "Please enter Acct Id...";
    /** COPAUS0C.cbl:278. */
    public static final String ACCT_ID_NOT_NUMERIC = "Acct Id must be Numeric ...";
    /** COPAUS0C.cbl:328. */
    public static final String INVALID_SELECTION = "Invalid selection. Valid value is S";
    /** COPAUS0C.cbl:381. */
    public static final String TOP_OF_PAGE = "You are already at the top of the page...";
    /** COPAUS0C.cbl:409. */
    public static final String BOTTOM_OF_PAGE = "You are already at the bottom of the page...";
    /** COPAUS1C.cbl:283. */
    public static final String LAST_AUTHORIZATION = "Already at the last Authorization...";
    /** COPAUS1C.cbl:535. */
    public static final String FRAUD_REMOVED = "AUTH FRAUD REMOVED...";
    /** COPAUS1C.cbl:537. */
    public static final String FRAUD_MARKED = "AUTH MARKED FRAUD...";
    /** COPAUS2C.cbl:201. */
    public static final String DB2_ADD_SUCCESS = "ADD SUCCESS";
    /** COPAUS2C.cbl:232. */
    public static final String DB2_UPDT_SUCCESS = "UPDT SUCCESS";

    /** COPAUA0C.cbl:576-577 — ERR-MESSAGE of the A001 warning. */
    public static final String CARD_NOT_FOUND_IN_XREF = "CARD NOT FOUND IN XREF";
    /** COPAUA0C.cbl:543-544 — ERR-MESSAGE of the A002 warning. */
    public static final String ACCT_NOT_FOUND_IN_XREF = "ACCT NOT FOUND IN XREF";
    /** COPAUA0C.cbl:589-590 — ERR-MESSAGE of the A003 warning. */
    public static final String CUST_NOT_FOUND_IN_XREF = "CUST NOT FOUND IN XREF";

    /** DFHRESP(NOTFND). */
    public static final int RESP_NOTFND = 13;

    private PendingAuthMessages() {
    }

    /** COPAUS0C.cbl:836-843 — the CXACAIX read miss. */
    public static String acctNotFoundInXref(String acctId11) {
        return "Account:" + acctId11 + " not found in XREF file. Resp:"
                + PendingAuthFormat.code9(RESP_NOTFND) + " Reas:" + PendingAuthFormat.code9(0);
    }

    /** COPAUS0C.cbl:886-893 — the ACCTDAT read miss. */
    public static String acctNotFoundInAcct(String acctId11) {
        return "Account:" + acctId11 + " not found in ACCT file. Resp:"
                + PendingAuthFormat.code9(RESP_NOTFND) + " Reas:" + PendingAuthFormat.code9(0);
    }

    /** COPAUS0C.cbl:937-944 — the CUSTDAT read miss. */
    public static String custNotFoundInCust(String custId9) {
        return "Customer:" + custId9 + " not found in CUST file. Resp:"
                + PendingAuthFormat.code9(RESP_NOTFND) + " Reas:" + PendingAuthFormat.code9(0);
    }
}
