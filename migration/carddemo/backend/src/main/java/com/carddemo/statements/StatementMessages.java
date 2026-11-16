package com.carddemo.statements;

/**
 * Message-line text of the Statements API. The account-filter edits reuse the Account View
 * (COACTVWC 2210-EDIT-ACCOUNT) wording verbatim so both screens reject a bad account id the same
 * way; the rest is new text for a non-legacy feature.
 */
public final class StatementMessages {

    /** COACTVWC:600-620 — blank account id. */
    public static final String NO_INPUT_RECEIVED = "No input received";
    /** COACTVWC:669-673 — non-numeric or zero account id (double space as in the COBOL literal). */
    public static final String INVALID_ACCOUNT_FILTER =
            "Account Filter must  be a non-zero 11 digit number";

    public static final String INVALID_PAGE = "Page must be a non-negative number";
    public static final String INVALID_STATEMENT_ID = "Statement id must be a positive number";
    public static final String SIGN_ON_REQUIRED = "Please sign on to view statements";

    private StatementMessages() {
    }

    public static String accountNotFound(String accountId11) {
        return "Account " + accountId11 + " not found";
    }

    public static String statementNotFound(long stmtId) {
        return "Statement " + stmtId + " not found";
    }
}
