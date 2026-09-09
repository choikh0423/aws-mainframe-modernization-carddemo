package com.carddemo.billpayment.exception;

import com.carddemo.billpayment.BillPaymentMessages;

/**
 * NOTFND on the ACCTDAT read (cbl:359-364) and — with the very same message
 * text, quirk Q-3 — on the CXACAIX read (cbl:423-428).
 */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException() {
        super(BillPaymentMessages.ACCOUNT_NOT_FOUND);
    }
}
