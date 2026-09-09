package com.carddemo.card.exception;

import com.carddemo.card.dto.CardErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the S-03 failures onto HTTP responses carrying the verbatim 3270
 * message text:
 *   - screen edit failure                    -> 400 (the ERRMSG literal),
 *   - CARDDAT NOTFND                         -> 404 "Did not find cards ...",
 *   - record changed before the rewrite      -> 409 "Record changed by some
 *     one else. Please review" plus the refreshed screen values.
 */
@RestControllerAdvice(basePackages = "com.carddemo.card")
public class CardManagementExceptionHandler {

    @ExceptionHandler(CardValidationException.class)
    public ResponseEntity<CardErrorResponse> handleValidation(CardValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new CardErrorResponse(ex.getMessage(), ex.getFields(), null));
    }

    @ExceptionHandler(CardNotFoundException.class)
    public ResponseEntity<CardErrorResponse> handleNotFound(CardNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new CardErrorResponse(ex.getMessage(), java.util.List.of(), null));
    }

    @ExceptionHandler(CardUpdateFailedException.class)
    public ResponseEntity<CardErrorResponse> handleUpdateFailed(CardUpdateFailedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new CardErrorResponse(ex.getMessage(), java.util.List.of(), null));
    }

    @ExceptionHandler(CardRecordChangedException.class)
    public ResponseEntity<CardErrorResponse> handleChanged(CardRecordChangedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new CardErrorResponse(ex.getMessage(), java.util.List.of(), ex.getRefreshed()));
    }
}
