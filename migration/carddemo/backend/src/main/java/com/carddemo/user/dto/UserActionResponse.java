package com.carddemo.user.dto;

/**
 * The green confirmation a successful CU01 add / CU02 update / CU03 delete
 * leaves on the screen, with the user id it names.
 */
public class UserActionResponse {

    private final String userId;
    private final String message;

    public UserActionResponse(String userId, String message) {
        this.userId = userId;
        this.message = message;
    }

    public String getUserId() { return userId; }
    public String getMessage() { return message; }
}
