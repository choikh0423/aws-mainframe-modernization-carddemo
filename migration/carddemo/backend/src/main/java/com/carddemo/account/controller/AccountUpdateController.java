package com.carddemo.account.controller;

import com.carddemo.account.dto.AccountUpdateRequest;
import com.carddemo.account.dto.AccountUpdateResponse;
import com.carddemo.account.service.AccountUpdateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CAUP / COACTUPC — update account. One endpoint per AID the legacy screen honours: ENTER on the
 * search key ({@code GET}), ENTER on a filled screen ({@code validate}) and F5 ({@code save}).
 * F12 is the client re-issuing the {@code GET}.
 */
@RestController
@RequestMapping("/api/accounts/{accountId}/update")
public class AccountUpdateController {

    private final AccountUpdateService service;

    public AccountUpdateController(AccountUpdateService service) {
        this.service = service;
    }

    @GetMapping
    public AccountUpdateResponse fetch(@PathVariable("accountId") String accountId) {
        return service.fetch(accountId);
    }

    @PostMapping("/validate")
    public AccountUpdateResponse validate(@PathVariable("accountId") String accountId,
                                          @RequestBody AccountUpdateRequest request) {
        return service.validate(accountId, request);
    }

    @PostMapping("/save")
    public AccountUpdateResponse save(@PathVariable("accountId") String accountId,
                                      @RequestBody AccountUpdateRequest request) {
        return service.save(accountId, request);
    }
}
