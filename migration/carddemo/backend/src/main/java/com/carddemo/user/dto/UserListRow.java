package com.carddemo.user.dto;

import com.carddemo.common.domain.SecUserRecord;

/**
 * One CU00 list line: the Sel / User ID / First Name / Last Name / Type columns
 * of map COUSR0A (app/bms/COUSR00.bms:107-181). The Sel flag itself is a screen
 * input, not data, so it is not carried here. {@code userType} is the raw
 * one-character SEC-USR-TYPE (FR-UL-17, quirk Q11).
 */
public class UserListRow {

    private final String userId;
    private final String firstName;
    private final String lastName;
    private final String userType;

    public UserListRow(String userId, String firstName, String lastName, String userType) {
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.userType = userType;
    }

    public static UserListRow from(SecUserRecord record) {
        return new UserListRow(
                trim(record.getSecUsrId()),
                trim(record.getSecUsrFname()),
                trim(record.getSecUsrLname()),
                trim(record.getSecUsrType()));
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public String getUserId() { return userId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getUserType() { return userType; }
}
