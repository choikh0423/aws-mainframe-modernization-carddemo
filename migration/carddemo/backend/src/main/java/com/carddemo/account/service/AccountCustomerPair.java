package com.carddemo.account.service;

import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CustomerRecord;

/** The ACCTDAT and CUSTDAT records a successful CAVW/CAUP lookup produces. */
public class AccountCustomerPair {

    private final AccountRecord account;
    private final CustomerRecord customer;

    public AccountCustomerPair(AccountRecord account, CustomerRecord customer) {
        this.account = account;
        this.customer = customer;
    }

    public AccountRecord getAccount() {
        return account;
    }

    public CustomerRecord getCustomer() {
        return customer;
    }
}
