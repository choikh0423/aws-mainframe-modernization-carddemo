package com.carddemo.common.dto;

/**
 * Error payload carrying the legacy 3270 ERRMSG text so the React screen can
 * surface the exact message COTRN01C would have shown.
 */
public class ErrorResponse {

    private final String message;

    public ErrorResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
