package com.carddemo.user.exception;

import com.carddemo.common.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the S-05 failure paths to HTTP responses carrying the exact legacy ERRMSG
 * text, so every screen can display the body's {@code message} unchanged:
 *   - field edits            -> 400 (e.g. "First Name can NOT be empty...")
 *   - nothing changed on CU02-> 400 "Please modify to update ..."
 *   - NOTFND                 -> 404 "User ID NOT found..."
 *   - DUPKEY/DUPREC on CU01  -> 409 "User ID already exist..."
 *   - any other file failure -> 500 "Unable to ..." (the WHEN OTHER literal)
 */
@RestControllerAdvice(basePackages = "com.carddemo.user")
public class UserExceptionHandler {

    @ExceptionHandler(UserValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(UserValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(UserNotModifiedException.class)
    public ResponseEntity<ErrorResponse> handleNotModified(UserNotModifiedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateUserIdException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateUserIdException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(UserStoreException.class)
    public ResponseEntity<ErrorResponse> handleStoreFailure(UserStoreException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(ex.getMessage()));
    }
}
