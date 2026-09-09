package com.carddemo.user.dto;

/**
 * The four editable CU02 fields (app/bms/COUSR02.bms:98-154). The user id is the
 * VSAM key and travels in the path, not the body — CU02 cannot change it
 * (FR-UU-15).
 */
public class UserUpdateRequest {

    private String firstName;
    private String lastName;
    private String password;
    private String userType;

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }
}
