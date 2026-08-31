package com.carddemo.batch.processor;

import com.carddemo.batch.model.CardXref;
import com.carddemo.batch.model.Transaction;
import com.carddemo.batch.model.TransactionCategory;
import com.carddemo.batch.model.TransactionType;
import com.carddemo.batch.repository.CardXrefRepository;
import com.carddemo.batch.repository.TransactionCategoryRepository;
import com.carddemo.batch.repository.TransactionTypeRepository;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Enriches Transaction records with lookup data for reporting.
 * Ported from CBTRN03C.cbl paragraphs:
 *   1500-A-LOOKUP-XREF
 *   1500-B-LOOKUP-TRANTYPE
 *   1500-C-LOOKUP-TRANCATG
 */
@Component
public class TransactionReportProcessor implements ItemProcessor<Transaction, Map<String, Object>> {

    private final CardXrefRepository cardXrefRepository;
    private final TransactionTypeRepository transactionTypeRepository;
    private final TransactionCategoryRepository transactionCategoryRepository;

    public TransactionReportProcessor(
            CardXrefRepository cardXrefRepository,
            TransactionTypeRepository transactionTypeRepository,
            TransactionCategoryRepository transactionCategoryRepository) {
        this.cardXrefRepository = cardXrefRepository;
        this.transactionTypeRepository = transactionTypeRepository;
        this.transactionCategoryRepository = transactionCategoryRepository;
    }

    @Override
    public Map<String, Object> process(Transaction item) {
        Map<String, Object> enriched = new HashMap<>();
        enriched.put("transaction", item);

        // 1500-A-LOOKUP-XREF: look up CardXref
        Optional<CardXref> xref = cardXrefRepository.findByCardNum(
                item.getCardNum() != null ? item.getCardNum().trim() : "");
        enriched.put("xref", xref.orElse(null));

        // 1500-B-LOOKUP-TRANTYPE: look up TransactionType description
        Optional<TransactionType> tranType = transactionTypeRepository.findById(
                item.getTranTypeCd() != null ? item.getTranTypeCd().trim() : "");
        enriched.put("tranTypeDesc",
                tranType.map(TransactionType::getDescription).orElse("UNKNOWN"));

        // 1500-C-LOOKUP-TRANCATG: look up TransactionCategory description
        Optional<TransactionCategory> tranCat =
                transactionCategoryRepository.findByTranTypeCdAndTranCatCd(
                        item.getTranTypeCd(), item.getTranCatCd());
        enriched.put("tranCatDesc",
                tranCat.map(TransactionCategory::getDescription).orElse("UNKNOWN"));

        return enriched;
    }
}
