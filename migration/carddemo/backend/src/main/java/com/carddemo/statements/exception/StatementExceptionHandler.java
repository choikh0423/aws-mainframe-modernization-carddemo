package com.carddemo.statements.exception;

import com.carddemo.common.dto.ErrorResponse;
import com.carddemo.statements.controller.StatementController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps Statements API failures to the shared {@link ErrorResponse} body. */
@RestControllerAdvice(assignableTypes = StatementController.class)
public class StatementExceptionHandler {

    @ExceptionHandler(StatementRequestException.class)
    public ResponseEntity<ErrorResponse> handleRequest(StatementRequestException ex) {
        return error(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(StatementNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(StatementNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(StatementSignOnRequiredException.class)
    public ResponseEntity<ErrorResponse> handleSignOn(StatementSignOnRequiredException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex);
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, RuntimeException ex) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(ex.getMessage()));
    }
}
