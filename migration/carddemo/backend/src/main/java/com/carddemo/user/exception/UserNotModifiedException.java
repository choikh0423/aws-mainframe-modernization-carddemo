package com.carddemo.user.exception;

import com.carddemo.user.UserMessages;

/**
 * CU02 PF5 with nothing changed: USR-MODIFIED-NO, so no REWRITE is issued and
 * the screen shows the red "Please modify to update ..." (COUSR02C.cbl:238-243).
 */
public class UserNotModifiedException extends RuntimeException {

    public UserNotModifiedException() {
        super(UserMessages.PLEASE_MODIFY_TO_UPDATE);
    }
}
