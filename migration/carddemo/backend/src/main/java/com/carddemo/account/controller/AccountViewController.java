package com.carddemo.account.controller;

import com.carddemo.account.dto.AccountViewResponse;
import com.carddemo.account.service.AccountViewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CAVW / COACTVWC — view account. */
@RestController
@RequestMapping("/api/accounts")
public class AccountViewController {

    private final AccountViewService service;

    public AccountViewController(AccountViewService service) {
        this.service = service;
    }

    @GetMapping("/{accountId}")
    public AccountViewResponse view(@PathVariable("accountId") String accountId) {
        return service.view(accountId);
    }
}
