package com.carddemo.transaction.exception;

import com.carddemo.transaction.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the CT02 add failures to HTTP responses carrying the exact legacy ERRMSG
 * text COTRN02C would have shown:
 *   - input edit / confirm failure -> 400 (FR-A3, FR-A7..A11)
 *   - account/card not in the xref  -> 404 (FR-A4, FR-A5)
 *   - generated Tran ID collides    -> 409 "Tran ID already exist..."
 */
@RestControllerAdvice
public class TransactionAddExceptionHandler {

    @ExceptionHandler(TransactionValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(TransactionValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(CardXrefNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleXrefNotFound(CardXrefNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateTranIdException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateTranIdException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(ex.getMessage()));
    }
}
