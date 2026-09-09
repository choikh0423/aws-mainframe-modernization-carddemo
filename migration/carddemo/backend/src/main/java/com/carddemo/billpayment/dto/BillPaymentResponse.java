package com.carddemo.billpayment.dto;

import com.carddemo.billpayment.BillPaymentMessages;

/**
 * One rendered CB00 screen — the pseudo-conversational SEND every COBIL00C path
 * ends with (cbl:242, 532). It carries the state of the three variable fields
 * plus the row-23 ERRMSG text and its colour, so the React screen can reproduce
 * the 3270 turn exactly.
 *
 * <p>{@code cursor} is the field COBIL00C put the cursor on with
 * {@code MOVE -1 TO ...L}: {@code ACTIDIN} or {@code CONFIRM}.
 */
public class BillPaymentResponse {

    /** The map field names, as the BMS map declares them. */
    public static final String CURSOR_ACCT_ID = "ACTIDIN";
    public static final String CURSOR_CONFIRM = "CONFIRM";

    /** ERRMSG's map colour: red by default (bms:127-130), green after a good WRITE (cbl:526). */
    public static final String COLOUR_RED = "RED";
    public static final String COLOUR_GREEN = "GREEN";

    private final String accountId;
    private final String currentBalance;
    private final String confirm;
    private final String message;
    private final String messageColour;
    private final String cursor;
    private final String tranId;
    private final boolean error;

    private BillPaymentResponse(String accountId, String currentBalance, String confirm,
                                String message, String messageColour, String cursor,
                                String tranId, boolean error) {
        this.accountId = accountId;
        this.currentBalance = currentBalance;
        this.confirm = confirm;
        this.message = message;
        this.messageColour = messageColour;
        this.cursor = cursor;
        this.tranId = tranId;
        this.error = error;
    }

    /**
     * The blank screen: first entry (cbl:114-115) and CLEAR-CURRENT-SCREEN after
     * a {@code N} confirm or PF4 (cbl:552-566). No message at all (quirk Q-7).
     */
    public static BillPaymentResponse cleared() {
        return new BillPaymentResponse("", "", "", "", COLOUR_RED, CURSOR_ACCT_ID, null, false);
    }

    /**
     * The balance-inquiry turn: a blank confirm on a payable account shows the
     * balance and {@code Confirm to make a bill payment...} (cbl:236-239).
     */
    public static BillPaymentResponse confirmPrompt(String accountId, String currentBalance) {
        return new BillPaymentResponse(accountId, currentBalance, "",
                BillPaymentMessages.CONFIRM_PROMPT, COLOUR_RED, CURSOR_CONFIRM, null, false);
    }

    /**
     * {@code You have nothing to pay...} (cbl:197-205). The balance was already
     * moved to the map (cbl:193-194), so this error turn still shows it.
     */
    public static BillPaymentResponse nothingToPay(String accountId, String currentBalance) {
        return new BillPaymentResponse(accountId, currentBalance, "",
                BillPaymentMessages.NOTHING_TO_PAY, COLOUR_RED, CURSOR_ACCT_ID, null, true);
    }

    /**
     * A completed payment: every field blanked and the green success text
     * (cbl:522-532).
     */
    public static BillPaymentResponse paid(String tranId) {
        return new BillPaymentResponse("", "", "", BillPaymentMessages.paymentSuccessful(tranId),
                COLOUR_GREEN, CURSOR_ACCT_ID, tranId, false);
    }

    public String getAccountId() { return accountId; }
    public String getCurrentBalance() { return currentBalance; }
    public String getConfirm() { return confirm; }
    public String getMessage() { return message; }
    public String getMessageColour() { return messageColour; }
    public String getCursor() { return cursor; }
    public String getTranId() { return tranId; }
    public boolean isError() { return error; }
}
