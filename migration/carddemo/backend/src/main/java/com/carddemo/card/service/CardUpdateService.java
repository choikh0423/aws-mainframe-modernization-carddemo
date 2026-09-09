package com.carddemo.card.service;

import com.carddemo.card.dto.CardUpdateRequest;
import com.carddemo.card.dto.CardUpdateResponse;
import com.carddemo.card.exception.CardNotFoundException;
import com.carddemo.card.exception.CardRecordChangedException;
import com.carddemo.card.exception.CardUpdateFailedException;
import com.carddemo.card.message.CardManagementMessages;
import com.carddemo.card.repository.CardBrowseRepository;
import com.carddemo.card.validator.CardSearchKeyValidator;
import com.carddemo.card.validator.CardUpdateValidator;
import com.carddemo.common.domain.CardRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * CCUP — Update Credit Card (COCRDUPC).
 *
 * <p>The legacy program is a CCUP-CHANGE-ACTION state machine
 * (COCRDUPC.cbl:948-1031):
 * fetch details, detect "no change", edit the fields, ask for PF5, then rewrite
 * under a READ UPDATE lock having re-compared the record against the values the
 * screen fetched. All of that is reproduced here; the only difference is that
 * the CCUP-OLD-DETAILS snapshot travels with the request instead of a COMMAREA.
 */
@Service
public class CardUpdateService {

    private final CardBrowseRepository repository;
    private final CardSearchKeyValidator keyValidator;
    private final CardUpdateValidator updateValidator;

    public CardUpdateService(CardBrowseRepository repository, CardSearchKeyValidator keyValidator,
                             CardUpdateValidator updateValidator) {
        this.repository = repository;
        this.keyValidator = keyValidator;
        this.updateValidator = updateValidator;
    }

    /** 9000-READ-DATA: fetch the card and present it for editing. */
    public CardUpdateResponse load(String acctId, String cardNum) {
        keyValidator.validateSearchKeys(acctId, cardNum);
        CardRecord record = read(cardNum);
        return CardUpdateResponse.fromRecord(CardUpdateResponse.State.SHOW_DETAILS,
                CardManagementMessages.SHOW_DETAILS, acctId.trim(), record);
    }

    @Transactional
    public CardUpdateResponse update(CardUpdateRequest request) {
        keyValidator.validateSearchKeys(request.getAcctId(), request.getCardNum());
        String acctId = request.getAcctId().trim();

        if (!hasSnapshot(request)) {
            // CCUP-DETAILS-NOT-FETCHED: the first pass only shows what is on file.
            return load(request.getAcctId(), request.getCardNum());
        }

        // 1205-COMPARE-OLD-NEW: the field group is compared case-insensitively
        // (COCRDUPC.cbl:1035-1063).
        if (unchanged(request)) {
            CardRecord onFile = read(request.getCardNum());
            return CardUpdateResponse.fromRecord(CardUpdateResponse.State.NO_CHANGES,
                    CardManagementMessages.NO_CHANGES_DETECTED, acctId, onFile);
        }

        updateValidator.validate(request.getNewName(), request.getNewStatus(),
                request.getNewExpiryMonth(), request.getNewExpiryYear());

        if (!request.isConfirmed()) {
            // CCUP-CHANGES-OK-NOT-CONFIRMED: nothing is written until PF5.
            return new CardUpdateResponse(CardUpdateResponse.State.CHANGES_OK_NOT_CONFIRMED,
                    CardManagementMessages.CHANGES_VALIDATED, acctId, request.getCardNum().trim(),
                    request.getNewName().toUpperCase(), request.getNewStatus().trim().toUpperCase(),
                    pad(request.getNewExpiryMonth(), 2), pad(request.getNewExpiryYear(), 4),
                    request.getOldExpiryDay(), request.getOldCvvCd());
        }

        CardRecord locked = repository.findForUpdate(request.getCardNum().trim())
                .orElseThrow(() -> new CardUpdateFailedException(CardManagementMessages.COULD_NOT_LOCK));

        // 9300-CHECK-CHANGE-IN-REC (COCRDUPC.cbl:1498-1519).
        if (!matchesSnapshot(locked, request)) {
            throw new CardRecordChangedException(CardManagementMessages.RECORD_CHANGED_BY_OTHER,
                    CardUpdateResponse.fromRecord(CardUpdateResponse.State.SHOW_DETAILS,
                            CardManagementMessages.RECORD_CHANGED_BY_OTHER, acctId, locked));
        }

        // The rewrite keeps card number, account and CVV and replaces name,
        // status and expiry year/month; the expiry day comes from the record the
        // screen fetched (COCRDUPC.cbl:1521-1539).
        locked.setEmbossedName(request.getNewName().toUpperCase());
        locked.setActiveStatus(request.getNewStatus().trim().toUpperCase());
        locked.setExpiraionDate(pad(request.getNewExpiryYear(), 4) + "-" + pad(request.getNewExpiryMonth(), 2)
                + "-" + request.getOldExpiryDay());

        CardRecord saved;
        try {
            saved = repository.saveAndFlush(locked);
        } catch (RuntimeException ex) {
            throw new CardUpdateFailedException(CardManagementMessages.UPDATE_FAILED);
        }

        return CardUpdateResponse.fromRecord(CardUpdateResponse.State.CHANGES_OKAYED_AND_DONE,
                CardManagementMessages.CHANGES_COMMITTED, acctId, saved);
    }

    private CardRecord read(String cardNum) {
        return repository.findById(cardNum.trim())
                .orElseThrow(() -> new CardNotFoundException(
                        CardManagementMessages.DID_NOT_FIND_ACCTCARD_COMBO));
    }

    private boolean hasSnapshot(CardUpdateRequest request) {
        return request.getOldName() != null && request.getOldStatus() != null
                && request.getOldExpiryMonth() != null && request.getOldExpiryYear() != null
                && request.getOldExpiryDay() != null && request.getNewName() != null
                && request.getNewStatus() != null && request.getNewExpiryMonth() != null
                && request.getNewExpiryYear() != null;
    }

    private boolean unchanged(CardUpdateRequest request) {
        return equalsIgnoreCaseTrimmed(request.getNewName(), request.getOldName())
                && equalsIgnoreCaseTrimmed(request.getNewStatus(), request.getOldStatus())
                && equalsIgnoreCaseTrimmed(pad(request.getNewExpiryMonth(), 2), request.getOldExpiryMonth())
                && equalsIgnoreCaseTrimmed(pad(request.getNewExpiryYear(), 4), request.getOldExpiryYear());
    }

    private boolean matchesSnapshot(CardRecord onFile, CardUpdateRequest request) {
        String expiry = onFile.getExpiraionDate();
        return Objects.equals(onFile.getCvvCd(), request.getOldCvvCd())
                && equalsIgnoreCaseTrimmed(onFile.getEmbossedName(), request.getOldName())
                && expiry.substring(0, 4).equals(request.getOldExpiryYear())
                && expiry.substring(5, 7).equals(request.getOldExpiryMonth())
                && expiry.substring(8, 10).equals(request.getOldExpiryDay())
                && equalsIgnoreCaseTrimmed(onFile.getActiveStatus(), request.getOldStatus());
    }

    private boolean equalsIgnoreCaseTrimmed(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        return a.equalsIgnoreCase(b);
    }

    /** The map fields are fixed width and zero-filled (PIC 9(2) / 9(4)). */
    private String pad(String value, int length) {
        String trimmed = value == null ? "" : value.trim();
        StringBuilder sb = new StringBuilder();
        while (sb.length() + trimmed.length() < length) {
            sb.append('0');
        }
        return sb + trimmed;
    }
}
