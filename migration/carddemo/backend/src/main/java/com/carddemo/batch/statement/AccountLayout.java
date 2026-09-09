package com.carddemo.batch.statement;

import com.carddemo.common.batch.FixedWidthRecord;
import com.carddemo.common.domain.AccountRecord;

import java.math.BigDecimal;

/**
 * ACCOUNT-RECORD, app/cpy/CVACT01Y.cpy (RECLN 300) - the record CBSTM03B returns
 * for an ACCTFILE keyed read (CBSTM03A.CBL:57, 412).
 */
final class AccountLayout {

    static final int LENGTH = 300;

    private AccountLayout() {
    }

    /** ACCT-ID PIC 9(11), as the 11 digits CBSTM03A moves into ST-ACCT-ID X(20). */
    static String accountId(String record) {
        return record.substring(0, 11);
    }

    /** ACCT-CURR-BAL PIC S9(10)V99. */
    static BigDecimal currentBalance(String record) {
        return new FixedWidthRecord(record, LENGTH).signed(12, 12, 2);
    }

    /** Renders the row back into the 300-byte VSAM record CBSTM03B hands over. */
    static String render(AccountRecord account) {
        StringBuilder record = new StringBuilder(LENGTH);
        record.append(StatementFormat.digits(account.getAcctId(), 11));
        record.append(StatementFormat.text(account.getActiveStatus(), 1));
        record.append(StatementFormat.zoned(account.getCurrBal(), 12, 2));
        record.append(StatementFormat.zoned(account.getCreditLimit(), 12, 2));
        record.append(StatementFormat.zoned(account.getCashCreditLimit(), 12, 2));
        record.append(StatementFormat.text(account.getOpenDate(), 10));
        record.append(StatementFormat.text(account.getExpiraionDate(), 10));
        record.append(StatementFormat.text(account.getReissueDate(), 10));
        record.append(StatementFormat.zoned(account.getCurrCycCredit(), 12, 2));
        record.append(StatementFormat.zoned(account.getCurrCycDebit(), 12, 2));
        record.append(StatementFormat.text(account.getAddrZip(), 10));
        record.append(StatementFormat.text(account.getGroupId(), 10));
        return StatementFormat.text(record.toString(), LENGTH);
    }
}
