package com.carddemo.account.service;

import com.carddemo.account.dto.AccountViewResponse;
import com.carddemo.account.validator.AccountFilterValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CAVW: edit the account filter, then read xref, account and customer in that order. */
@Service
public class AccountViewService {

    private final AccountFilterValidator filterValidator;
    private final AccountLookupService lookupService;

    public AccountViewService(AccountFilterValidator filterValidator,
                              AccountLookupService lookupService) {
        this.filterValidator = filterValidator;
        this.lookupService = lookupService;
    }

    @Transactional(readOnly = true)
    public AccountViewResponse view(String accountId) {
        long editedAccountId = filterValidator.validateViewFilter(accountId);
        AccountCustomerPair records = lookupService.lookup(editedAccountId);
        return AccountViewResponse.from(records.getAccount(), records.getCustomer());
    }
}
