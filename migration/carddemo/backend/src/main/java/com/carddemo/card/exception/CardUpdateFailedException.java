package com.carddemo.card.exception;

/**
 * The rewrite leg of COCRDUPC failed: either the READ UPDATE could not take the
 * record ("Could not lock record for update", COCRDUPC.cbl:1438-1446) or the
 * REWRITE itself was rejected ("Update of record failed",
 * COCRDUPC.cbl:1540-1552).
 */
public class CardUpdateFailedException extends RuntimeException {

    public CardUpdateFailedException(String message) {
        super(message);
    }
}
