package com.carddemo.transaction.controller;

import com.carddemo.transaction.dto.AddTransactionRequest;
import com.carddemo.transaction.dto.AddTransactionResponse;
import com.carddemo.transaction.dto.CopyLastTransactionResponse;
import com.carddemo.transaction.exception.ValidationException;
import com.carddemo.transaction.service.TransactionAddService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller replacing CICS transaction CT02 (COTRN02C).
 *
 * Endpoint mapping to COBOL:
 *   POST /api/transactions          -> ENTER + confirm=Y (full submit)
 *   POST /api/transactions/validate -> ENTER without confirm (validate only)
 *   GET  /api/transactions/last     -> PF5 (Copy Last Transaction)
 *
 * CICS SEND MAP / RECEIVE MAP -> HTTP request/response
 * CICS RETURN TRANSID         -> HTTP response completes the cycle
 * BMS CURSOR positioning      -> errorField in response DTO
 */
@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = "*")
public class TransactionAddController {

    private final TransactionAddService service;

    public TransactionAddController(TransactionAddService service) {
        this.service = service;
    }

    /**
     * Full submission: validate + add transaction.
     * Replaces ENTER key with confirm=Y in COBOL.
     *
     * Returns 201 on success, 422 on validation error.
     */
    @PostMapping
    public ResponseEntity<AddTransactionResponse> addTransaction(
            @RequestBody AddTransactionRequest request) {
        try {
            AddTransactionResponse response = service.processSubmission(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(AddTransactionResponse.error(e.getMessage(), e.getErrorField()));
        }
    }

    /**
     * Validate only: run all validation without writing a record.
     * Replaces ENTER key with confirm=N/blank in COBOL.
     *
     * Returns resolved card/acct and normalized amount on success.
     */
    @PostMapping("/validate")
    public ResponseEntity<AddTransactionResponse> validateTransaction(
            @RequestBody AddTransactionRequest request) {
        try {
            AddTransactionResponse response = service.validateOnly(request);
            return ResponseEntity.ok(response);
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(AddTransactionResponse.error(e.getMessage(), e.getErrorField()));
        }
    }

    /**
     * Copy last transaction data (PF5).
     * Replaces COPY-LAST-TRAN-DATA paragraph.
     *
     * Requires acctId or cardNum query param for key validation.
     */
    @GetMapping("/last")
    public ResponseEntity<?> copyLastTransaction(
            @RequestParam(required = false) String acctId,
            @RequestParam(required = false) String cardNum) {
        try {
            AddTransactionRequest keyReq = new AddTransactionRequest();
            keyReq.setAcctId(acctId);
            keyReq.setCardNum(cardNum);
            CopyLastTransactionResponse response = service.copyLastTransaction(keyReq);
            return ResponseEntity.ok(response);
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(AddTransactionResponse.error(e.getMessage(), e.getErrorField()));
        }
    }
}
