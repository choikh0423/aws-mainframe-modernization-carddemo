package com.carddemo.transaction.controller;

import com.carddemo.transaction.dto.TransactionViewResponse;
import com.carddemo.transaction.service.TransactionViewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CT01 — Transaction View (COTRN01C). Exposes the single-key read the legacy
 * screen performed on ENTER:
 *   GET /api/transactions/{id} -> full transaction detail (FR-V1),
 *   404 "Transaction ID NOT found..." (FR-V2),
 *   400 "Tran ID can NOT be empty..." for a blank id (FR-V3).
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionViewController {

    private final TransactionViewService service;

    public TransactionViewController(TransactionViewService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public TransactionViewResponse view(@PathVariable("id") String id) {
        return service.view(id);
    }
}
