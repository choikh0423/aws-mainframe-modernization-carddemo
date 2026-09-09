package com.carddemo.transaction.exception;

import com.carddemo.common.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the CT00 list-filter failure to an HTTP response carrying the exact
 * legacy ERRMSG text:
 *   - non-numeric Tran ID filter -> 400 "Tran ID must be Numeric ..." (FR-L7).
 */
@RestControllerAdvice
public class TransactionListExceptionHandler {

    @ExceptionHandler(NonNumericTranIdException.class)
    public ResponseEntity<ErrorResponse> handleNonNumeric(NonNumericTranIdException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getMessage()));
    }
}
