package com.carddemo.batch.service;

import com.carddemo.batch.model.CardXref;
import com.carddemo.batch.repository.CardXrefRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for card cross-reference lookups.
 * Replaces VSAM random READ on XREFFILE by primary key (card number)
 * and alternate key (account ID).
 */
@Service
public class CardXrefLookupService {

    private final CardXrefRepository cardXrefRepository;

    public CardXrefLookupService(CardXrefRepository cardXrefRepository) {
        this.cardXrefRepository = cardXrefRepository;
    }

    public Optional<CardXref> findByCardNum(String cardNum) {
        return cardXrefRepository.findByCardNum(cardNum);
    }

    public List<CardXref> findByAcctId(Long acctId) {
        return cardXrefRepository.findByAcctId(acctId);
    }
}
