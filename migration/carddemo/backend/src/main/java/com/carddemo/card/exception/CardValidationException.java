package com.carddemo.card.exception;

import java.util.List;

/**
 * A CardManagement screen edit failed (COCRDLIC 2200-EDIT-INPUTS, COCRDSLC
 * 2000-EDIT-MAP-INPUTS, COCRDUPC 1200-EDIT-MAP-INPUTS). Carries the exact
 * legacy message the program would have moved into the ERRMSG line plus the
 * fields the program flagged, so the screen can highlight them the same way.
 */
public class CardValidationException extends RuntimeException {

    private final List<String> fields;

    public CardValidationException(String message, String... fields) {
        super(message);
        this.fields = List.of(fields);
    }

    public List<String> getFields() {
        return fields;
    }
}
