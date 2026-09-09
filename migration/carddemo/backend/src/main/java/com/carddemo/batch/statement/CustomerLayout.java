package com.carddemo.batch.statement;

import com.carddemo.common.domain.CustomerRecord;

/**
 * CUSTOMER-RECORD, app/cpy/CUSTREC.cpy (RECLN 500) - the record CBSTM03B returns
 * for a CUSTFILE keyed read and CBSTM03A moves into its own copy of the copybook
 * (CBSTM03A.CBL:55, 388).
 */
final class CustomerLayout {

    static final int LENGTH = 500;

    private CustomerLayout() {
    }

    static String firstName(String record) {
        return record.substring(9, 34);
    }

    static String middleName(String record) {
        return record.substring(34, 59);
    }

    static String lastName(String record) {
        return record.substring(59, 84);
    }

    static String addressLine1(String record) {
        return record.substring(84, 134);
    }

    static String addressLine2(String record) {
        return record.substring(134, 184);
    }

    static String addressLine3(String record) {
        return record.substring(184, 234);
    }

    static String stateCode(String record) {
        return record.substring(234, 236);
    }

    static String countryCode(String record) {
        return record.substring(236, 239);
    }

    static String zip(String record) {
        return record.substring(239, 249);
    }

    static String ficoScore(String record) {
        return record.substring(329, 332);
    }

    /** Renders the row back into the 500-byte VSAM record CBSTM03B hands over. */
    static String render(CustomerRecord customer) {
        StringBuilder record = new StringBuilder(LENGTH);
        record.append(StatementFormat.digits(customer.getCustId(), 9));
        record.append(StatementFormat.text(customer.getFirstName(), 25));
        record.append(StatementFormat.text(customer.getMiddleName(), 25));
        record.append(StatementFormat.text(customer.getLastName(), 25));
        record.append(StatementFormat.text(customer.getAddrLine1(), 50));
        record.append(StatementFormat.text(customer.getAddrLine2(), 50));
        record.append(StatementFormat.text(customer.getAddrLine3(), 50));
        record.append(StatementFormat.text(customer.getAddrStateCd(), 2));
        record.append(StatementFormat.text(customer.getAddrCountryCd(), 3));
        record.append(StatementFormat.text(customer.getAddrZip(), 10));
        record.append(StatementFormat.text(customer.getPhoneNum1(), 15));
        record.append(StatementFormat.text(customer.getPhoneNum2(), 15));
        record.append(StatementFormat.digits(customer.getSsn() == null ? 0L : customer.getSsn(), 9));
        record.append(StatementFormat.text(customer.getGovtIssuedId(), 20));
        record.append(StatementFormat.text(customer.getDobYyyyMmDd(), 10));
        record.append(StatementFormat.text(customer.getEftAccountId(), 10));
        record.append(StatementFormat.text(customer.getPriCardHolderInd(), 1));
        record.append(StatementFormat.digits(
                customer.getFicoCreditScore() == null ? 0L : customer.getFicoCreditScore(), 3));
        return StatementFormat.text(record.toString(), LENGTH);
    }
}
