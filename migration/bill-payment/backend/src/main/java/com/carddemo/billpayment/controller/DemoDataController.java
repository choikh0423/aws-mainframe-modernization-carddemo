package com.carddemo.billpayment.controller;

import com.carddemo.billpayment.entity.Transaction;
import com.carddemo.billpayment.repository.AccountRepository;
import com.carddemo.billpayment.repository.TransactionRepository;
import com.carddemo.billpayment.service.BillPaymentService;
import com.carddemo.billpayment.service.SeedDataLoader;
import com.carddemo.billpayment.util.CobolRecords;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Data inspection for the side-by-side comparison; records are rendered in the COBOL layouts. */
@RestController
@CrossOrigin
@RequestMapping("/api/demo")
public class DemoDataController {

    private final SeedDataLoader seed;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    public DemoDataController(SeedDataLoader seed, AccountRepository accounts,
                              TransactionRepository transactions) {
        this.seed = seed;
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        seed.reload();
        return Map.of("accounts", accounts.count(), "transactions", transactions.count());
    }

    @GetMapping("/accounts/{acctId}")
    public ResponseEntity<Map<String, Object>> account(@PathVariable String acctId) {
        return accounts.findById(acctId).<ResponseEntity<Map<String, Object>>>map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("acctId", a.getAcctId());
            m.put("currentBalance", BillPaymentService.formatBalance(a.getCurrBal()));
            m.put("record", CobolRecords.formatAccount(a));
            return ResponseEntity.ok(m);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/transactions/latest")
    public List<Map<String, Object>> latest(@RequestParam(defaultValue = "5") int limit) {
        return transactions.findLatest(PageRequest.of(0, limit)).stream().map(DemoDataController::view)
                .toList();
    }

    private static Map<String, Object> view(Transaction t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tranId", t.getTranId());
        m.put("amount", t.getAmount());
        m.put("description", t.getDescription());
        m.put("cardNum", t.getCardNum());
        m.put("record", CobolRecords.formatTransaction(t));
        return m;
    }
}
