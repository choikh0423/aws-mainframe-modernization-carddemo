package com.carddemo.account.dto;

import com.carddemo.account.util.AccountFormat;
import com.carddemo.account.util.AccountMessages;
import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CustomerRecord;

/**
 * The CACTVWA map after 3000-SEND-MAP (COACTVWC:880-1050): every value already edited the way the
 * BMS PICOUTs edit it, plus the info line the program writes.
 */
public class AccountViewResponse {

    public String accountId;
    public String activeStatus;
    public String openDate;
    public String creditLimit;
    public String expirationDate;
    public String cashCreditLimit;
    public String reissueDate;
    public String currBal;
    public String currCycCredit;
    public String groupId;
    public String currCycDebit;

    public String custId;
    public String ssn;
    public String dateOfBirth;
    public String ficoScore;
    public String firstName;
    public String middleName;
    public String lastName;
    public String addrLine1;
    public String addrLine2;
    public String city;
    public String state;
    public String zip;
    public String country;
    public String phone1;
    public String govtIssuedId;
    public String phone2;
    public String eftAccountId;
    public String priCardHolderInd;

    public String infoMessage;

    public static AccountViewResponse from(AccountRecord account, CustomerRecord customer) {
        AccountViewResponse response = new AccountViewResponse();
        response.accountId = AccountFormat.accountId(account.getAcctId());
        response.activeStatus = account.getActiveStatus();
        response.openDate = account.getOpenDate();
        response.creditLimit = AccountFormat.amount(account.getCreditLimit());
        response.expirationDate = account.getExpiraionDate();
        response.cashCreditLimit = AccountFormat.amount(account.getCashCreditLimit());
        response.reissueDate = account.getReissueDate();
        response.currBal = AccountFormat.amount(account.getCurrBal());
        response.currCycCredit = AccountFormat.amount(account.getCurrCycCredit());
        response.groupId = account.getGroupId();
        response.currCycDebit = AccountFormat.amount(account.getCurrCycDebit());

        response.custId = AccountFormat.customerId(customer.getCustId());
        response.ssn = AccountFormat.ssn(customer.getSsn());
        response.dateOfBirth = customer.getDobYyyyMmDd();
        response.ficoScore = AccountFormat.fico(customer.getFicoCreditScore());
        response.firstName = customer.getFirstName();
        response.middleName = customer.getMiddleName();
        response.lastName = customer.getLastName();
        response.addrLine1 = customer.getAddrLine1();
        response.addrLine2 = customer.getAddrLine2();
        response.city = customer.getAddrLine3();
        response.state = customer.getAddrStateCd();
        response.zip = customer.getAddrZip();
        response.country = customer.getAddrCountryCd();
        response.phone1 = customer.getPhoneNum1();
        response.govtIssuedId = customer.getGovtIssuedId();
        response.phone2 = customer.getPhoneNum2();
        response.eftAccountId = customer.getEftAccountId();
        response.priCardHolderInd = customer.getPriCardHolderInd();

        response.infoMessage = AccountMessages.VIEW_DETAILS_SHOWN;
        return response;
    }
}
