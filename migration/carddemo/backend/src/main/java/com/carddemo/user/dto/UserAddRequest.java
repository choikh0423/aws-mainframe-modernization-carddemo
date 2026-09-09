package com.carddemo.user.dto;

/**
 * The five CU01 screen fields, in map order (app/bms/COUSR01.bms:80-149), each
 * carried as a raw string so the COBOL edits — not Bean Validation — decide what
 * is acceptable (FR-UA-2, FR-UA-10, FR-UA-11).
 */
public class UserAddRequest {

    private String firstName;
    private String lastName;
    private String userId;
    private String password;
    private String userType;

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }
}
