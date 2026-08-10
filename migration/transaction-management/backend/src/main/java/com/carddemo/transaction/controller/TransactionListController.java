package com.carddemo.transaction.controller;

import com.carddemo.transaction.dto.TransactionListResponse;
import com.carddemo.transaction.service.TransactionListService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CT00 — Transaction List (COTRN00C). Exposes the paged TRANSACT browse the
 * legacy screen performed on ENTER / PF7 / PF8:
 *   GET /api/transactions                      -> first 10 by Tran ID (FR-L1),
 *   GET /api/transactions?startId=<id>         -> list from that Tran ID (FR-L2),
 *   GET /api/transactions?startId=<id>&dir=next-> next 10  (FR-L3, PF8),
 *   GET /api/transactions?startId=<id>&dir=prev-> prev 10  (FR-L4, PF7),
 *   400 "Tran ID must be Numeric ..." for a non-numeric filter (FR-L7).
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionListController {

    private final TransactionListService service;

    public TransactionListController(TransactionListService service) {
        this.service = service;
    }

    @GetMapping
    public TransactionListResponse list(
            @RequestParam(name = "startId", required = false) String startId,
            @RequestParam(name = "dir", required = false) String dir) {
        return service.list(startId, dir);
    }
}
