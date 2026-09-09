package com.carddemo.card.service;

import com.carddemo.card.dto.CardDetailResponse;
import com.carddemo.card.exception.CardNotFoundException;
import com.carddemo.card.message.CardManagementMessages;
import com.carddemo.card.repository.CardBrowseRepository;
import com.carddemo.card.validator.CardSearchKeyValidator;
import com.carddemo.common.domain.CardRecord;
import org.springframework.stereotype.Service;

/**
 * CCDL — View Credit Card detail (COCRDSLC).
 *
 * <p>Both keys are mandatory edits, but the read itself is 9000-READ-CARD-FILE,
 * keyed on the card number alone (COCRDSLC.cbl:726-805). The account number is
 * therefore validated and echoed but does not constrain the lookup: an
 * account/card pair that does not belong together still displays the card
 * (quirk Q-3). The alternate-index-by-account path 9150-GETCARD-BYACCT, which
 * owns "Did not find this account in cards database", is never performed.
 */
@Service
public class CardDetailService {

    private final CardBrowseRepository repository;
    private final CardSearchKeyValidator keyValidator;

    public CardDetailService(CardBrowseRepository repository, CardSearchKeyValidator keyValidator) {
        this.repository = repository;
        this.keyValidator = keyValidator;
    }

    public CardDetailResponse detail(String acctId, String cardNum) {
        keyValidator.validateSearchKeys(acctId, cardNum);

        CardRecord record = repository.findById(cardNum.trim())
                .orElseThrow(() -> new CardNotFoundException(
                        CardManagementMessages.DID_NOT_FIND_ACCTCARD_COMBO));

        String expiry = record.getExpiraionDate();
        return new CardDetailResponse(acctId.trim(), record.getCardNum(), record.getEmbossedName(),
                expiry.substring(5, 7), expiry.substring(0, 4), record.getActiveStatus(),
                CardManagementMessages.DISPLAYING_REQUESTED_DETAILS);
    }
}
