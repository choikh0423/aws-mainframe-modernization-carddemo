package com.carddemo.transaction.service;

import com.carddemo.transaction.dto.TransactionViewResponse;
import com.carddemo.transaction.entity.TransactionRecord;
import com.carddemo.transaction.exception.EmptyTranIdException;
import com.carddemo.transaction.exception.TransactionNotFoundException;
import com.carddemo.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;

/**
 * CT01 — View a Transaction (COTRN01C).
 *
 * Reproduces COTRN01C's PROCESS-ENTER-KEY / READ-TRANSACT-FILE flow 1:1:
 *   1. empty/blank Tran ID -> "Tran ID can NOT be empty..." (COTRN01C.cbl:147-152)
 *   2. READ TRANSACT by key -> not found -> "Transaction ID NOT found..." (COTRN01C.cbl:283-288)
 *   3. found -> return every CVTRA05Y display field (COTRN01C.cbl:176-192)
 *
 * The legacy program uses the raw X(16) TRNIDIN field content as the VSAM
 * RIDFLD (no re-padding), so the lookup key is matched exactly.
 */
@Service
public class TransactionViewService {

    private final TransactionRepository repository;

    public TransactionViewService(TransactionRepository repository) {
        this.repository = repository;
    }

    public TransactionViewResponse view(String tranId) {
        if (tranId == null || tranId.trim().isEmpty()) {
            throw new EmptyTranIdException();
        }
        TransactionRecord record = repository.findById(tranId)
                .orElseThrow(TransactionNotFoundException::new);
        return TransactionViewResponse.from(record);
    }
}
