package com.carddemo.billpayment.controller;

import com.carddemo.billpayment.dto.BillPaymentRequest;
import com.carddemo.billpayment.dto.BillPaymentResponse;
import com.carddemo.billpayment.service.BillPaymentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CB00 — Bill Payment (COBIL00C). The legacy screen had exactly one active key,
 * ENTER (PF3/PF4 only navigate or blank the map), so the stream exposes exactly
 * one endpoint: one call = one ENTER turn of map COBIL0A.
 *
 * <p>{@code POST /api/billpay/screen}
 *   -&gt; 200 with the repainted screen for the turns that show a field
 *      (balance inquiry, "You have nothing to pay...", the {@code N} decline and
 *      a completed payment);
 *   -&gt; 400 with the verbatim ERRMSG for the input edits;
 *   -&gt; 404 "Account ID NOT found..." when ACCTDAT or CXACAIX has no record;
 *   -&gt; 409 "Tran ID already exist..." when the generated key collides.
 */
@RestController
@RequestMapping("/api/billpay")
public class BillPaymentController {

    private final BillPaymentService billPaymentService;

    public BillPaymentController(BillPaymentService billPaymentService) {
        this.billPaymentService = billPaymentService;
    }

    @PostMapping("/screen")
    public BillPaymentResponse enter(@RequestBody BillPaymentRequest request) {
        return billPaymentService.enter(request);
    }
}
