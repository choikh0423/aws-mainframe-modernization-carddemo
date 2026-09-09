package com.carddemo.authshell;

/**
 * The outcome of a COSGN00C sign-on attempt.
 *
 * @param signedOn    true when READ-USER-SEC-FILE matched and the password compared equal
 * @param userId      CDEMO-USER-ID, upper-cased as COSGN00C does
 * @param userType    CDEMO-USER-TYPE: 'A' or 'U'
 * @param nextProgram COADM01C for administrators, COMEN01C otherwise; null on failure
 * @param message     the screen message; empty when the sign-on succeeded
 * @param errorField  the field COSGN00C puts the cursor on (-1 in the length field)
 */
public record SignOnResult(boolean signedOn, String userId, String userType,
                           String nextProgram, String message, String errorField) {

    public static SignOnResult success(String userId, String userType, String nextProgram) {
        return new SignOnResult(true, userId, userType, nextProgram, "", null);
    }

    public static SignOnResult failure(String message, String errorField) {
        return new SignOnResult(false, null, null, null, message, errorField);
    }
}
