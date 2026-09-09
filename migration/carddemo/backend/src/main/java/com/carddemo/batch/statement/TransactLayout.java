package com.carddemo.batch.statement;

import com.carddemo.common.domain.TransactionRecord;

/**
 * TRAN-RECORD, app/cpy/CVTRA05Y.cpy (RECLN 350) - the TRANSACT KSDS record
 * CREASTMT.JCL STEP010 sorts (CREASTMT.JCL:45).
 */
final class TransactLayout {

    static final int LENGTH = 350;

    private TransactLayout() {
    }

    /** TRAN-CARD-NUM, bytes 263-278, the primary SORT field. */
    static String cardNumber(String record) {
        return record.substring(262, 278);
    }

    /** TRAN-ID, bytes 1-16, the secondary SORT field. */
    static String transactionId(String record) {
        return record.substring(0, 16);
    }

    /** Renders the row into the 350-byte record SORT reads from SORTIN. */
    static String render(TransactionRecord transaction) {
        StringBuilder record = new StringBuilder(LENGTH);
        record.append(StatementFormat.text(transaction.getId(), 16));
        record.append(StatementFormat.text(transaction.getTypeCd(), 2));
        record.append(StatementFormat.digits(transaction.getCatCd(), 4));
        record.append(StatementFormat.text(transaction.getSource(), 10));
        record.append(StatementFormat.text(transaction.getDescription(), 100));
        record.append(StatementFormat.zoned(transaction.getAmount(), 11, 2));
        record.append(StatementFormat.digits(transaction.getMerchantId(), 9));
        record.append(StatementFormat.text(transaction.getMerchantName(), 50));
        record.append(StatementFormat.text(transaction.getMerchantCity(), 50));
        record.append(StatementFormat.text(transaction.getMerchantZip(), 10));
        record.append(StatementFormat.text(transaction.getCardNum(), 16));
        record.append(StatementFormat.text(transaction.getOrigTs(), 26));
        record.append(StatementFormat.text(transaction.getProcTs(), 26));
        return StatementFormat.text(record.toString(), LENGTH);
    }
}
