package com.carddemo.batch.statement;

import java.math.BigDecimal;

/** Fixed-width records shaped like the ones CBSTM03B hands to CBSTM03A. */
final class StatementFixtures {

    private StatementFixtures() {
    }

    static String customer(String first, String middle, String last,
                           String address1, String address2, String address3,
                           String state, String country, String zip, String fico) {
        StringBuilder record = new StringBuilder();
        record.append(StatementFormat.digits(123456789L, 9));
        record.append(StatementFormat.text(first, 25));
        record.append(StatementFormat.text(middle, 25));
        record.append(StatementFormat.text(last, 25));
        record.append(StatementFormat.text(address1, 50));
        record.append(StatementFormat.text(address2, 50));
        record.append(StatementFormat.text(address3, 50));
        record.append(StatementFormat.text(state, 2));
        record.append(StatementFormat.text(country, 3));
        record.append(StatementFormat.text(zip, 10));
        record.append(StatementFormat.text("", 80));
        record.append(StatementFormat.text(fico, 3));
        return StatementFormat.text(record.toString(), CustomerLayout.LENGTH);
    }

    static String account(String accountId, BigDecimal balance) {
        return StatementFormat.text(
                StatementFormat.text(accountId, 11)
                        + "Y"
                        + StatementFormat.zoned(balance, 12, 2),
                AccountLayout.LENGTH);
    }

    static String xref(String cardNumber, long customerId, long accountId) {
        return StatementFormat.text(
                StatementFormat.text(cardNumber, 16)
                        + StatementFormat.digits(customerId, 9)
                        + StatementFormat.digits(accountId, 11),
                XrefLayout.LENGTH);
    }

    /** A TRNX-RECORD in the COSTM01 layout. */
    static String work(String cardNumber, String transactionId, String description, BigDecimal amount) {
        StringBuilder record = new StringBuilder();
        record.append(StatementFormat.text(cardNumber, 16));
        record.append(StatementFormat.text(transactionId, 16));
        record.append(StatementFormat.text("01", 2));
        record.append(StatementFormat.digits(5L, 4));
        record.append(StatementFormat.text("POS TERM", 10));
        record.append(StatementFormat.text(description, 100));
        record.append(StatementFormat.zoned(amount, 11, 2));
        record.append(StatementFormat.digits(400000001L, 9));
        record.append(StatementFormat.text("Merchant", 50));
        record.append(StatementFormat.text("Seattle", 50));
        record.append(StatementFormat.text("98101", 10));
        record.append(StatementFormat.text("2022-01-01 10:00:00.000000", 26));
        record.append(StatementFormat.text("2022-01-02 10:00:00.000000", 26));
        return StatementFormat.text(record.toString(), TrnxLayout.LENGTH);
    }

    /** A TRAN-RECORD in the original CVTRA05Y layout, as STEP010 reads it. */
    static String transact(String cardNumber, String transactionId, BigDecimal amount) {
        StringBuilder record = new StringBuilder();
        record.append(StatementFormat.text(transactionId, 16));
        record.append(StatementFormat.text("01", 2));
        record.append(StatementFormat.digits(5L, 4));
        record.append(StatementFormat.text("POS TERM", 10));
        record.append(StatementFormat.text("Coffee shop", 100));
        record.append(StatementFormat.zoned(amount, 11, 2));
        record.append(StatementFormat.digits(400000001L, 9));
        record.append(StatementFormat.text("Merchant", 50));
        record.append(StatementFormat.text("Seattle", 50));
        record.append(StatementFormat.text("98101", 10));
        record.append(StatementFormat.text(cardNumber, 16));
        record.append(StatementFormat.text("2022-01-01 10:00:00.000000", 26));
        record.append(StatementFormat.text("2022-01-02 10:00:00.999999", 26));
        return StatementFormat.text(record.toString(), TransactLayout.LENGTH);
    }
}
