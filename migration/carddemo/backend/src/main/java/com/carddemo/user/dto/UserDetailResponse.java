package com.carddemo.user.dto;

import com.carddemo.common.domain.SecUserRecord;

/**
 * The record CU02 and CU03 paint after a successful fetch
 * (COUSR02C.cbl:334-339, COUSR03C.cbl:281-286). CU02's map shows the password
 * in a DRK (non-display) field and CU03's map has no password field at all, but
 * both programs read the same 80-byte USRSEC record; the password is returned
 * because CU02 needs it in the field it will rewrite.
 */
public class UserDetailResponse {

    private final String userId;
    private final String firstName;
    private final String lastName;
    private final String password;
    private final String userType;

    public UserDetailResponse(String userId, String firstName, String lastName,
                              String password, String userType) {
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.password = password;
        this.userType = userType;
    }

    public static UserDetailResponse from(SecUserRecord record) {
        return new UserDetailResponse(
                trim(record.getSecUsrId()),
                trim(record.getSecUsrFname()),
                trim(record.getSecUsrLname()),
                trim(record.getSecUsrPwd()),
                trim(record.getSecUsrType()));
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public String getUserId() { return userId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPassword() { return password; }
    public String getUserType() { return userType; }
}
