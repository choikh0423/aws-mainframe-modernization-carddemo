package com.carddemo.billpayment.exception;

import com.carddemo.common.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the CB00 failures that COBIL00C reports with a bare ERRMSG line (no
 * screen data) onto HTTP responses carrying that exact text:
 *   - failed input edit                -> 400 (FR-BP-10, FR-BP-13)
 *   - ACCTDAT / CXACAIX NOTFND         -> 404 (FR-BP-21, FR-BP-31)
 *   - generated TRAN-ID already exists -> 409 (FR-BP-37)
 *
 * The turns that also repaint a field (balance inquiry, "nothing to pay",
 * decline, successful payment) are 200 responses carrying the whole screen.
 */
@RestControllerAdvice(basePackages = "com.carddemo.billpayment")
public class BillPaymentExceptionHandler {

    @ExceptionHandler(BillPaymentValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(BillPaymentValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(AccountNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateTranIdException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateTranIdException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }
}
