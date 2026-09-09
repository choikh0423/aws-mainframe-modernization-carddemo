package com.carddemo.account.dto;

import com.carddemo.account.util.AccountFormat;
import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CustomerRecord;

/**
 * The CACTUPA map, field for field: one string per enterable position, with dates, phone numbers
 * and the SSN split exactly as COACTUP.bms splits them. This is the shape the operator types and
 * the shape the legacy program keeps in {@code ACUP-OLD-DETAILS} / {@code ACUP-NEW-DETAILS}
 * (COACTUPC:380-870), so requests carry both copies instead of a server side commarea.
 *
 * <p>Public fields keep this 42 position screen record readable; it is a data carrier only.
 */
public class AccountFields {

    public String accountId;
    public String activeStatus;
    public String currBal;
    public String creditLimit;
    public String cashCreditLimit;
    public String currCycCredit;
    public String currCycDebit;
    public String openYear;
    public String openMonth;
    public String openDay;
    public String expiryYear;
    public String expiryMonth;
    public String expiryDay;
    public String reissueYear;
    public String reissueMonth;
    public String reissueDay;
    public String groupId;

    public String custId;
    public String ssnPart1;
    public String ssnPart2;
    public String ssnPart3;
    public String ficoScore;
    public String dobYear;
    public String dobMonth;
    public String dobDay;
    public String firstName;
    public String middleName;
    public String lastName;
    public String addrLine1;
    public String addrLine2;
    public String city;
    public String state;
    public String zip;
    public String country;
    public String phone1Area;
    public String phone1Prefix;
    public String phone1Line;
    public String phone2Area;
    public String phone2Prefix;
    public String phone2Line;
    public String govtIssuedId;
    public String eftAccountId;
    public String priCardHolderInd;

    /** 3202-SHOW-ORIGINAL-VALUES (COACTUPC:2787-2864). */
    public static AccountFields from(AccountRecord account, CustomerRecord customer) {
        AccountFields fields = new AccountFields();
        fields.accountId = AccountFormat.accountId(account.getAcctId());
        fields.activeStatus = account.getActiveStatus();
        fields.currBal = AccountFormat.amount(account.getCurrBal());
        fields.creditLimit = AccountFormat.amount(account.getCreditLimit());
        fields.cashCreditLimit = AccountFormat.amount(account.getCashCreditLimit());
        fields.currCycCredit = AccountFormat.amount(account.getCurrCycCredit());
        fields.currCycDebit = AccountFormat.amount(account.getCurrCycDebit());
        fields.openYear = AccountFormat.datePart(account.getOpenDate(), 0, 4);
        fields.openMonth = AccountFormat.datePart(account.getOpenDate(), 5, 7);
        fields.openDay = AccountFormat.datePart(account.getOpenDate(), 8, 10);
        fields.expiryYear = AccountFormat.datePart(account.getExpiraionDate(), 0, 4);
        fields.expiryMonth = AccountFormat.datePart(account.getExpiraionDate(), 5, 7);
        fields.expiryDay = AccountFormat.datePart(account.getExpiraionDate(), 8, 10);
        fields.reissueYear = AccountFormat.datePart(account.getReissueDate(), 0, 4);
        fields.reissueMonth = AccountFormat.datePart(account.getReissueDate(), 5, 7);
        fields.reissueDay = AccountFormat.datePart(account.getReissueDate(), 8, 10);
        fields.groupId = account.getGroupId();

        fields.custId = AccountFormat.customerId(customer.getCustId());
        String ssn = customer.getSsn() == null ? "" : String.format("%09d", customer.getSsn());
        fields.ssnPart1 = ssn.isEmpty() ? "" : ssn.substring(0, 3);
        fields.ssnPart2 = ssn.isEmpty() ? "" : ssn.substring(3, 5);
        fields.ssnPart3 = ssn.isEmpty() ? "" : ssn.substring(5);
        fields.ficoScore = AccountFormat.fico(customer.getFicoCreditScore());
        fields.dobYear = AccountFormat.datePart(customer.getDobYyyyMmDd(), 0, 4);
        fields.dobMonth = AccountFormat.datePart(customer.getDobYyyyMmDd(), 5, 7);
        fields.dobDay = AccountFormat.datePart(customer.getDobYyyyMmDd(), 8, 10);
        fields.firstName = customer.getFirstName();
        fields.middleName = customer.getMiddleName();
        fields.lastName = customer.getLastName();
        fields.addrLine1 = customer.getAddrLine1();
        fields.addrLine2 = customer.getAddrLine2();
        fields.city = customer.getAddrLine3();
        fields.state = customer.getAddrStateCd();
        fields.zip = customer.getAddrZip();
        fields.country = customer.getAddrCountryCd();
        fields.phone1Area = AccountFormat.phonePart(customer.getPhoneNum1(), 1, 4);
        fields.phone1Prefix = AccountFormat.phonePart(customer.getPhoneNum1(), 5, 8);
        fields.phone1Line = AccountFormat.phonePart(customer.getPhoneNum1(), 9, 13);
        fields.phone2Area = AccountFormat.phonePart(customer.getPhoneNum2(), 1, 4);
        fields.phone2Prefix = AccountFormat.phonePart(customer.getPhoneNum2(), 5, 8);
        fields.phone2Line = AccountFormat.phonePart(customer.getPhoneNum2(), 9, 13);
        fields.govtIssuedId = customer.getGovtIssuedId();
        fields.eftAccountId = customer.getEftAccountId();
        fields.priCardHolderInd = customer.getPriCardHolderInd();
        return fields;
    }
}
