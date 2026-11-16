package com.carddemo.statements;

/** Display edits shared by the Statements API responses. */
public final class StatementFormat {

    private StatementFormat() {
    }

    /** ACCT-ID PIC 9(11): zero-padded to 11 digits. */
    public static String accountId(long acctId) {
        return String.format("%011d", acctId);
    }

    /** CUST-ID PIC 9(09): zero-padded to 9 digits. */
    public static String customerId(long custId) {
        return String.format("%09d", custId);
    }

    /** Every digit but the last four replaced by {@code *}, keeping the original length. */
    public static String maskCard(String cardNum) {
        if (cardNum == null) {
            return null;
        }
        String card = cardNum.trim();
        int keep = Math.min(4, card.length());
        return "*".repeat(card.length() - keep) + card.substring(card.length() - keep);
    }
}
