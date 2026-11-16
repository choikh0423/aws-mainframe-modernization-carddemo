package com.carddemo.batch.statement;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything CBSTM03A holds while it prints one statement: the CUSTOMER-RECORD
 * and ACCOUNT-RECORD it read for the current CARDXREF entry, and the TRNX-RECORDs
 * it found for that card in WS-TRNX-TABLE (CBSTM03A.CBL:316-329, 416-431).
 *
 * <p>The records are the raw fixed-width images CBSTM03B returns, so the printing
 * code moves the same bytes the COBOL does.
 *
 * @param cardNumber XREF-CARD-NUM of the statement
 * @param customerRecord CUSTOMER-RECORD, 500 bytes
 * @param accountRecord ACCOUNT-RECORD, 300 bytes
 * @param transactionRecords TRNX-RECORDs for the card, 350 bytes each
 */
public record Statement(String cardNumber,
                        String customerRecord,
                        String accountRecord,
                        List<String> transactionRecords) {

    /** ACCT-ID PIC 9(11) of the account record. */
    public long accountId() {
        return Long.parseLong(AccountLayout.accountId(accountRecord));
    }

    /** The sum of the card's TRNX-AMTs: the value printed as Total EXP. */
    public BigDecimal totalAmount() {
        BigDecimal total = BigDecimal.ZERO;
        for (String record : transactionRecords) {
            total = total.add(TrnxLayout.amount(record));
        }
        return total;
    }
}
