package com.carddemo.transaction.controller;

import com.carddemo.transaction.exception.TransactionAddFailedException;
import com.carddemo.transaction.exception.TransactionValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TransactionExceptionHandler {

    @ExceptionHandler(TransactionValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(TransactionValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage(), e.getField()));
    }

    @ExceptionHandler(TransactionAddFailedException.class)
    public ResponseEntity<ErrorResponse> handleAddFailed(TransactionAddFailedException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(e.getMessage(), null));
    }
}
