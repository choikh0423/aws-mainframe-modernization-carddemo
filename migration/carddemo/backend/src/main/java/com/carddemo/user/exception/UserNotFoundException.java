package com.carddemo.user.exception;

import com.carddemo.user.UserMessages;

/**
 * DFHRESP(NOTFND) on the USRSEC read, rewrite or delete
 * (COUSR02C.cbl:340-346, 377-383; COUSR03C.cbl:287-293, 323-329).
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super(UserMessages.USER_ID_NOT_FOUND);
    }
}
