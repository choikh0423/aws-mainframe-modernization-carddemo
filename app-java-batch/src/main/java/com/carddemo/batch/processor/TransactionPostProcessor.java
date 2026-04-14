package com.carddemo.batch.processor;

import com.carddemo.batch.model.DailyTransaction;
import com.carddemo.batch.model.Transaction;
import com.carddemo.batch.service.DateValidationService;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * Transforms a validated DailyTransaction into a Transaction entity.
 * Ported from CBTRN02C.cbl paragraph 2000-POST-TRANSACTION:
 * copies fields from daily transaction record into the transaction entity
 * and generates processing timestamp.
 */
@Component
public class TransactionPostProcessor
        implements ItemProcessor<DailyTransaction, Transaction> {

    private final DateValidationService dateValidationService;

    public TransactionPostProcessor(DateValidationService dateValidationService) {
        this.dateValidationService = dateValidationService;
    }

    @Override
    public Transaction process(DailyTransaction item) {
        Transaction transaction = new Transaction();
        transaction.setTranTypeCd(item.getTranTypeCd());
        transaction.setTranCatCd(item.getTranCatCd());
        transaction.setTranSource(item.getTranSource());
        transaction.setTranDesc(item.getTranDesc());
        transaction.setTranAmt(item.getTranAmt());
        transaction.setMerchantId(item.getMerchantId());
        transaction.setMerchantName(item.getMerchantName());
        transaction.setMerchantCity(item.getMerchantCity());
        transaction.setMerchantZip(item.getMerchantZip());
        transaction.setCardNum(item.getCardNum());
        transaction.setOrigTimestamp(item.getOrigTimestamp());
        transaction.setProcTimestamp(dateValidationService.generateDb2Timestamp());
        return transaction;
    }
}
