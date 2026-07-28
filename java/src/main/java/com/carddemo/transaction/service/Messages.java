package com.carddemo.transaction.service;

/** User-facing messages, copied verbatim from COTRN02C.cbl. */
public final class Messages {

    public static final String ACCT_OR_CARD_REQUIRED = "Account or Card Number must be entered...";
    public static final String ACCT_ID_NOT_NUMERIC = "Account ID must be Numeric...";
    public static final String ACCT_ID_NOT_FOUND = "Account ID NOT found...";
    public static final String CARD_NUM_NOT_NUMERIC = "Card Number must be Numeric...";
    public static final String CARD_NUM_NOT_FOUND = "Card Number NOT found...";

    public static final String TYPE_CD_EMPTY = "Type CD can NOT be empty...";
    public static final String CATEGORY_CD_EMPTY = "Category CD can NOT be empty...";
    public static final String SOURCE_EMPTY = "Source can NOT be empty...";
    public static final String DESCRIPTION_EMPTY = "Description can NOT be empty...";
    public static final String AMOUNT_EMPTY = "Amount can NOT be empty...";
    public static final String ORIG_DATE_EMPTY = "Orig Date can NOT be empty...";
    public static final String PROC_DATE_EMPTY = "Proc Date can NOT be empty...";
    public static final String MERCHANT_ID_EMPTY = "Merchant ID can NOT be empty...";
    public static final String MERCHANT_NAME_EMPTY = "Merchant Name can NOT be empty...";
    public static final String MERCHANT_CITY_EMPTY = "Merchant City can NOT be empty...";
    public static final String MERCHANT_ZIP_EMPTY = "Merchant Zip can NOT be empty...";

    public static final String TYPE_CD_NOT_NUMERIC = "Type CD must be Numeric...";
    public static final String CATEGORY_CD_NOT_NUMERIC = "Category CD must be Numeric...";
    public static final String MERCHANT_ID_NOT_NUMERIC = "Merchant ID must be Numeric...";
    public static final String AMOUNT_FORMAT = "Amount should be in format -99999999.99";
    public static final String ORIG_DATE_FORMAT = "Orig Date should be in format YYYY-MM-DD";
    public static final String PROC_DATE_FORMAT = "Proc Date should be in format YYYY-MM-DD";
    public static final String ORIG_DATE_INVALID = "Orig Date - Not a valid date...";
    public static final String PROC_DATE_INVALID = "Proc Date - Not a valid date...";

    public static final String CONFIRM_TO_ADD = "Confirm to add this transaction...";
    public static final String CONFIRM_INVALID = "Invalid value. Valid values are (Y/N)...";

    public static final String TRAN_ID_EXISTS = "Tran ID already exist...";
    public static final String UNABLE_TO_ADD = "Unable to Add Transaction...";

    private Messages() {
    }

    /** WRITE-TRANSACT-FILE success message; TRAN-ID is delimited by SPACE in the COBOL STRING. */
    public static String addedSuccessfully(String tranId) {
        return "Transaction added successfully.  Your Tran ID is " + tranId.trim() + ".";
    }
}
