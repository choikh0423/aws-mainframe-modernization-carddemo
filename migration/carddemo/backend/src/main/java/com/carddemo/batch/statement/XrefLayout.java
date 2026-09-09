package com.carddemo.batch.statement;

import com.carddemo.common.domain.CardXrefRecord;

/**
 * CARD-XREF-RECORD, app/cpy/CVACT03Y.cpy (RECLN 50) - the record CBSTM03B returns
 * for a sequential XREFFILE read (CBSTM03A.CBL:53, 364).
 */
final class XrefLayout {

    static final int LENGTH = 50;

    private XrefLayout() {
    }

    /** XREF-CARD-NUM PIC X(16). */
    static String cardNumber(String record) {
        return record.substring(0, 16);
    }

    /** XREF-CUST-ID PIC 9(09), passed to CBSTM03B as a 9-byte key. */
    static String customerId(String record) {
        return record.substring(16, 25);
    }

    /** XREF-ACCT-ID PIC 9(11), passed to CBSTM03B as an 11-byte key. */
    static String accountId(String record) {
        return record.substring(25, 36);
    }

    /** Renders the row back into the 50-byte VSAM record CBSTM03B hands over. */
    static String render(CardXrefRecord xref) {
        return StatementFormat.text(
                StatementFormat.text(xref.getCardNum(), 16)
                        + StatementFormat.digits(xref.getCustId(), 9)
                        + StatementFormat.digits(xref.getAcctId(), 11),
                LENGTH);
    }
}
