package com.carddemo.user.exception;

/**
 * The {@code WHEN OTHER} arm of a USRSEC file operation — any response that is
 * neither NORMAL nor the specific NOTFND/DUPKEY case. The message is the
 * literal the failing screen shows: "Unable to lookup User...",
 * "Unable to Add User..." or "Unable to Update User..."
 * (COUSR00C.cbl:608-613; COUSR01C.cbl:267-272; COUSR02C.cbl:384-389;
 * COUSR03C.cbl:330-334).
 */
public class UserStoreException extends RuntimeException {

    public UserStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
