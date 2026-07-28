package com.carddemo.transaction.api;

import com.carddemo.transaction.service.TransactionAddService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST front end for CICS transaction CT02 (program COTRN02C). */
@RestController
@RequestMapping("/api/transactions")
public class TransactionAddController {

    private final TransactionAddService service;

    public TransactionAddController(TransactionAddService service) {
        this.service = service;
    }

    /** ENTER key on map COTRN2A. */
    @PostMapping("/add")
    public ResponseEntity<TransactionAddResponse> add(@RequestBody TransactionAddRequest request) {
        TransactionAddResponse response = service.processEnterKey(request);
        return ResponseEntity.status(response.isError() ? HttpStatus.BAD_REQUEST : HttpStatus.OK).body(response);
    }

    /** PF5 - copy last transaction into the screen fields. */
    @PostMapping("/copy-last")
    public ResponseEntity<TransactionAddResponse> copyLast(@RequestBody TransactionAddRequest request) {
        TransactionAddResponse response = service.copyLastTransaction(request);
        return ResponseEntity.status(response.isError() ? HttpStatus.BAD_REQUEST : HttpStatus.OK).body(response);
    }
}
