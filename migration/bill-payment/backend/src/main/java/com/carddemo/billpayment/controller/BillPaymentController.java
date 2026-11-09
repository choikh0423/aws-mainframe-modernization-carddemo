package com.carddemo.billpayment.controller;

import com.carddemo.billpayment.dto.BillPaymentRequest;
import com.carddemo.billpayment.dto.BillPaymentScreen;
import com.carddemo.billpayment.service.BillPaymentService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Replaces SEND/RECEIVE MAP('COBIL0A') + RETURN TRANSID('CB00') with a REST round trip. */
@RestController
@CrossOrigin
@RequestMapping("/api/bill-payment")
public class BillPaymentController {

    private final BillPaymentService service;

    public BillPaymentController(BillPaymentService service) {
        this.service = service;
    }

    @GetMapping
    public BillPaymentScreen initial() {
        return service.initialScreen();
    }

    @PostMapping
    public BillPaymentScreen submit(@RequestBody BillPaymentRequest request) {
        return service.handle(request);
    }
}
