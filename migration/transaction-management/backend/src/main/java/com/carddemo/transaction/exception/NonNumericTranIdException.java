package com.carddemo.transaction.exception;

/**
 * CT00 filter validation failure: the TRNIDIN start-from Tran ID was entered but
 * is not numeric. Carries the exact legacy ERRMSG text COTRN00C moves into
 * WS-MESSAGE (COTRN00C.cbl:209-218): "Tran ID must be Numeric ...".
 */
public class NonNumericTranIdException extends RuntimeException {

    public NonNumericTranIdException() {
        super("Tran ID must be Numeric ...");
    }
}
