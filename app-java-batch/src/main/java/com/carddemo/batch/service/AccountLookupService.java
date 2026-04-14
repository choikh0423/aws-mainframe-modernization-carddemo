package com.carddemo.batch.service;

import com.carddemo.batch.model.Account;
import com.carddemo.batch.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for account lookups.
 * Replaces VSAM random READ on ACCTFILE by primary key (account ID).
 */
@Service
public class AccountLookupService {

    private final AccountRepository accountRepository;

    public AccountLookupService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Optional<Account> findById(Long acctId) {
        return accountRepository.findById(acctId);
    }
}
