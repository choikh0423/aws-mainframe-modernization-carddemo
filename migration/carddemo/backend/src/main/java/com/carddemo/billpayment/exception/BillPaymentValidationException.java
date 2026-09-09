package com.carddemo.billpayment.exception;

/**
 * A screen edit COBIL00C rejects before touching a file: an empty Acct ID
 * (cbl:158-164) or a Confirm character that is not Y/y/N/n/blank (cbl:185-190).
 * The message is the verbatim ERRMSG text; the cursor position it implies is
 * reproduced by the screen (Acct ID, except for the confirm edit).
 */
public class BillPaymentValidationException extends RuntimeException {

    public BillPaymentValidationException(String message) {
        super(message);
    }
}
