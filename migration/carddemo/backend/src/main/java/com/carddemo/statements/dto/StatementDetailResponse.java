package com.carddemo.statements.dto;

import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CustomerRecord;
import com.carddemo.statements.StatementArchiveRecord;
import com.carddemo.statements.StatementFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One statement plus the account and customer header fields a statement view shows. Account and
 * customer fields are null when the master record no longer exists; the archived statement is
 * still served.
 */
public record StatementDetailResponse(long stmtId, String acctId, String cardNum, String cycle,
                                      int txnCount, BigDecimal totalAmount,
                                      LocalDateTime generatedTs, Account account,
                                      Customer customer) {

    public record Account(String activeStatus, BigDecimal currBal, BigDecimal creditLimit,
                          BigDecimal cashCreditLimit, BigDecimal currCycCredit,
                          BigDecimal currCycDebit) {

        static Account from(AccountRecord a) {
            return a == null ? null : new Account(a.getActiveStatus(), a.getCurrBal(),
                    a.getCreditLimit(), a.getCashCreditLimit(), a.getCurrCycCredit(),
                    a.getCurrCycDebit());
        }
    }

    public record Customer(String custId, String firstName, String middleName, String lastName,
                           String addrLine1, String addrLine2, String addrLine3, String state,
                           String country, String zip) {

        static Customer from(CustomerRecord c) {
            return c == null ? null : new Customer(StatementFormat.customerId(c.getCustId()),
                    trim(c.getFirstName()), trim(c.getMiddleName()), trim(c.getLastName()),
                    trim(c.getAddrLine1()), trim(c.getAddrLine2()), trim(c.getAddrLine3()),
                    trim(c.getAddrStateCd()), trim(c.getAddrCountryCd()), trim(c.getAddrZip()));
        }

        private static String trim(String value) {
            return value == null ? null : value.stripTrailing();
        }
    }

    public static StatementDetailResponse from(StatementArchiveRecord s, AccountRecord account,
                                               CustomerRecord customer) {
        return new StatementDetailResponse(s.getStmtId(), StatementFormat.accountId(s.getAcctId()),
                StatementFormat.maskCard(s.getCardNum()), s.getCycle(), s.getTxnCount(),
                s.getTotalAmount(), s.getGeneratedTs(), Account.from(account),
                Customer.from(customer));
    }
}
