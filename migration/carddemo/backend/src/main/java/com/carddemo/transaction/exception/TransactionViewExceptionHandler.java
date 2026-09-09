package com.carddemo.transaction.exception;

import com.carddemo.common.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the CT01 view failures to HTTP responses carrying the exact legacy
 * ERRMSG text:
 *   - blank Tran ID     -> 400 "Tran ID can NOT be empty..." (FR-V3)
 *   - key not on file   -> 404 "Transaction ID NOT found..." (FR-V2)
 */
@RestControllerAdvice
public class TransactionViewExceptionHandler {

    @ExceptionHandler(EmptyTranIdException.class)
    public ResponseEntity<ErrorResponse> handleEmpty(EmptyTranIdException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(TransactionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ex.getMessage()));
    }
}
