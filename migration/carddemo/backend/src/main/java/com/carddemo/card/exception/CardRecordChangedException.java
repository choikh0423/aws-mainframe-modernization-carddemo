package com.carddemo.card.exception;

import com.carddemo.card.dto.CardUpdateResponse;

/**
 * 9300-CHECK-CHANGE-IN-REC found the record on file no longer matching the
 * values this screen fetched (COCRDUPC.cbl:1498-1519). Nothing is written and
 * the screen is refreshed with the values now on file, exactly as the legacy
 * program re-populates CCUP-OLD-DETAILS.
 */
public class CardRecordChangedException extends RuntimeException {

    private final CardUpdateResponse refreshed;

    public CardRecordChangedException(String message, CardUpdateResponse refreshed) {
        super(message);
        this.refreshed = refreshed;
    }

    public CardUpdateResponse getRefreshed() {
        return refreshed;
    }
}
