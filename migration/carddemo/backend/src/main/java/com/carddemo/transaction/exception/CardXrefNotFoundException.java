package com.carddemo.transaction.exception;

/**
 * Raised when the CARDXREF lookup returns DFHRESP(NOTFND) in COTRN02C:
 *   - account not in the AIX -> "Account ID NOT found..." (COTRN02C.cbl:591-596)
 *   - card not in the xref   -> "Card Number NOT found..." (COTRN02C.cbl:624-629)
 * The message is the verbatim legacy text; it maps to HTTP 404.
 */
public class CardXrefNotFoundException extends RuntimeException {

    public static final String ACCOUNT_NOT_FOUND = "Account ID NOT found...";
    public static final String CARD_NOT_FOUND = "Card Number NOT found...";

    public CardXrefNotFoundException(String message) {
        super(message);
    }
}
