package com.carddemo.billpayment.exception;

import com.carddemo.billpayment.BillPaymentMessages;

/** DUPKEY/DUPREC on the TRANSACT write (cbl:533-539). */
public class DuplicateTranIdException extends RuntimeException {

    public DuplicateTranIdException() {
        super(BillPaymentMessages.DUPLICATE_TRAN_ID);
    }
}
