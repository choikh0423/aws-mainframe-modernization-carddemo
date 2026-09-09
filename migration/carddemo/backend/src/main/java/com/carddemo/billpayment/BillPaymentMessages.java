package com.carddemo.billpayment;

/**
 * The COBIL00C screen literals, reproduced verbatim from the COBOL source so
 * the migrated CB00 screen shows exactly what the 3270 map showed. Line numbers
 * refer to {@code app/cbl/COBIL00C.cbl}.
 */
public final class BillPaymentMessages {

    private BillPaymentMessages() {
    }

    /** cbl:161 — the Acct ID field was left blank. */
    public static final String ACCT_ID_EMPTY = "Acct ID can NOT be empty...";
    /** cbl:187 — the Confirm field held something other than Y/y/N/n/blank. */
    public static final String INVALID_CONFIRM = "Invalid value. Valid values are (Y/N)...";
    /** cbl:361, 425 — ACCTDAT NOTFND, and (quirk Q-3) CXACAIX NOTFND too. */
    public static final String ACCOUNT_NOT_FOUND = "Account ID NOT found...";
    /** cbl:201 — the balance is zero or negative. */
    public static final String NOTHING_TO_PAY = "You have nothing to pay...";
    /** cbl:237 — a balance inquiry turn: the operator has not confirmed yet. */
    public static final String CONFIRM_PROMPT = "Confirm to make a bill payment...";
    /** cbl:536 — the generated TRAN-ID already exists. */
    public static final String DUPLICATE_TRAN_ID = "Tran ID already exist...";

    /**
     * cbl:527-531 — the green success text. The first literal ends with a blank
     * and the second starts with one, so two spaces follow the first period.
     */
    public static String paymentSuccessful(String tranId) {
        return "Payment successful.  Your Transaction ID is " + tranId + ".";
    }
}
