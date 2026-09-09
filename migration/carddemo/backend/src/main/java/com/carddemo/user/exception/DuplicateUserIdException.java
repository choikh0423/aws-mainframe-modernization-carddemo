package com.carddemo.user.exception;

import com.carddemo.user.UserMessages;

/**
 * DFHRESP(DUPKEY)/DFHRESP(DUPREC) on the CU01 write (COUSR01C.cbl:260-266).
 */
public class DuplicateUserIdException extends RuntimeException {

    public DuplicateUserIdException() {
        super(UserMessages.USER_ID_ALREADY_EXIST);
    }
}
