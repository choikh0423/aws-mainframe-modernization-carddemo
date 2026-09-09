package com.carddemo.reporting.exception;

import com.carddemo.reporting.controller.ReportSubmissionController;
import com.carddemo.reporting.dto.ReportScreenErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns the CR00 failures into HTTP responses carrying the exact ERRMSG text and
 * the field CORPT00C left the cursor on. Scoped to this stream's controller so
 * no other stream's responses are affected.
 */
@RestControllerAdvice(assignableTypes = ReportSubmissionController.class)
public class ReportingExceptionHandler {

    /** An edit failure: the legacy program redisplayed the map with ERRMSG set. */
    @ExceptionHandler(ReportValidationException.class)
    public ResponseEntity<ReportScreenErrorResponse> handleValidation(ReportValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ReportScreenErrorResponse(ex.getMessage(), ex.getCursor(), ex.getDateFields()));
    }

    /** The job could not be submitted — the migrated {@code WRITEQ TD} failure. */
    @ExceptionHandler(ReportJobSubmissionException.class)
    public ResponseEntity<ReportScreenErrorResponse> handleSubmission(ReportJobSubmissionException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ReportScreenErrorResponse(ex.getMessage(), ex.getCursor(), null));
    }
}
