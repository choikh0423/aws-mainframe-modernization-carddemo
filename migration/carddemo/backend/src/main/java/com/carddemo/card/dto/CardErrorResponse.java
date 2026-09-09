package com.carddemo.card.dto;

import java.util.List;

/**
 * Error payload for the CardManagement screens: the verbatim ERRMSG text, the
 * fields the legacy program flagged (so the React screen can highlight the same
 * ones), and - for the "record changed by some one else" path - the refreshed
 * screen values COCRDUPC re-displays.
 */
public class CardErrorResponse {

    private final String message;
    private final List<String> fields;
    private final CardUpdateResponse refreshed;

    public CardErrorResponse(String message, List<String> fields, CardUpdateResponse refreshed) {
        this.message = message;
        this.fields = fields;
        this.refreshed = refreshed;
    }

    public String getMessage() { return message; }
    public List<String> getFields() { return fields; }
    public CardUpdateResponse getRefreshed() { return refreshed; }
}
