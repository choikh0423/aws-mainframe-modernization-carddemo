package com.carddemo.transaction.controller;

import com.carddemo.transaction.dto.AddTransactionRequest;
import com.carddemo.transaction.dto.AddTransactionResponse;
import com.carddemo.transaction.service.TransactionAddService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP entry point replacing CICS transaction CT02 (program COTRN02C).
 */
@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionAddService transactionAddService;

    public TransactionController(TransactionAddService transactionAddService) {
        this.transactionAddService = transactionAddService;
    }

    @PostMapping
    public ResponseEntity<AddTransactionResponse> add(@RequestBody AddTransactionRequest request) {
        AddTransactionResponse response = transactionAddService.addTransaction(request);
        HttpStatus status = response.isAdded() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }
}
