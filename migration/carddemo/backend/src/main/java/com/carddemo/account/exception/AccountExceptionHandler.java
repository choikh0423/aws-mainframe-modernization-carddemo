package com.carddemo.account.exception;

import com.carddemo.common.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the CAVW/CAUP failures to HTTP responses carrying the exact legacy ERRMSG text.
 */
@RestControllerAdvice
public class AccountExceptionHandler {

    @ExceptionHandler(AccountFilterException.class)
    public ResponseEntity<ErrorResponse> handleFilter(AccountFilterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(AccountValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(AccountValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(AccountNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(RecordLockException.class)
    public ResponseEntity<ErrorResponse> handleLock(RecordLockException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(StaleRecordException.class)
    public ResponseEntity<ErrorResponse> handleStale(StaleRecordException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(AccountUpdateFailedException.class)
    public ResponseEntity<ErrorResponse> handleUpdateFailed(AccountUpdateFailedException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(ex.getMessage()));
    }
}
