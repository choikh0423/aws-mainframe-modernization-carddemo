package com.carddemo.transaction.controller;

import com.carddemo.transaction.dto.CardXrefResolveResponse;
import com.carddemo.transaction.dto.TransactionAddRequest;
import com.carddemo.transaction.dto.TransactionAddResponse;
import com.carddemo.transaction.dto.TransactionViewResponse;
import com.carddemo.transaction.service.CardXrefResolveService;
import com.carddemo.transaction.service.TransactionAddService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CT02 — Transaction Add (COTRN02C). Exposes the add write and the account/card
 * cross-reference resolution the legacy screen performed on ENTER:
 *   POST /api/transactions              -> validate + write, 201 with the green
 *                                          "Transaction added successfully..." text (FR-A6),
 *                                          400 for edit failures (FR-A3/A7..A11),
 *                                          404 for a missing xref (FR-A4/A5),
 *                                          409 "Tran ID already exist..." on a dup key;
 *   GET  /api/cardxref/resolve?accountId= -> resolve the card from an account (FR-A1),
 *   GET  /api/cardxref/resolve?cardNumber=-> resolve the account from a card (FR-A2).
 */
@RestController
public class TransactionAddController {

    private final TransactionAddService addService;
    private final CardXrefResolveService resolveService;

    public TransactionAddController(TransactionAddService addService,
                                    CardXrefResolveService resolveService) {
        this.addService = addService;
        this.resolveService = resolveService;
    }

    @PostMapping("/api/transactions")
    public ResponseEntity<TransactionAddResponse> add(@RequestBody TransactionAddRequest request) {
        TransactionAddResponse response = addService.add(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/cardxref/resolve")
    public CardXrefResolveResponse resolve(
            @RequestParam(name = "accountId", required = false) String accountId,
            @RequestParam(name = "cardNumber", required = false) String cardNumber) {
        return resolveService.resolve(accountId, cardNumber);
    }

    /**
     * PF5 copy-last (FR-A12): the most recent transaction used to pre-fill the
     * form (COTRN02C COPY-LAST-TRAN-DATA, cbl:471-495). 200 with the record, or
     * 204 when the TRANSACT file is empty. Literal path takes precedence over the
     * {@code /api/transactions/{id}} view mapping.
     */
    @GetMapping("/api/transactions/latest")
    public ResponseEntity<TransactionViewResponse> latest() {
        return addService.getLatest()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
