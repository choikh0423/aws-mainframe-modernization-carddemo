package com.carddemo.user.validator;

import com.carddemo.user.UserMessages;
import com.carddemo.user.domain.UserFields;
import com.carddemo.user.dto.UserAddRequest;
import com.carddemo.user.dto.UserUpdateRequest;
import com.carddemo.user.exception.UserValidationException;
import org.springframework.stereotype.Component;

/**
 * The S-05 field edits, ported 1:1 from the {@code EVALUATE TRUE} blocks of
 * COUSR01C PROCESS-ENTER-KEY (cbl:115-152), COUSR02C UPDATE-USER-INFO
 * (cbl:177-213) and the empty-key guards of COUSR02C/COUSR03C.
 *
 * <p>Each COBOL arm moves an ERRMSG and PERFORMs the send paragraph, whose
 * trailing {@code EXEC CICS RETURN} ends the transaction, so the <em>first</em>
 * failure in source order is the one the operator sees. Throwing on the first
 * failing check reproduces that ordering.
 *
 * <p>These are the only edits the legacy programs perform: there is no
 * user-type domain check, no id length or character-set rule and no password
 * rule (quirks Q1/Q2, FR-UA-10, FR-UA-11, FR-UU-17).
 */
@Component
public class UserValidator {

    /** COUSR01C.cbl:115-152 — First Name, Last Name, User ID, Password, User Type. */
    public void validateAdd(UserAddRequest request) {
        require(request.getFirstName(), UserMessages.FIRST_NAME_EMPTY);
        require(request.getLastName(), UserMessages.LAST_NAME_EMPTY);
        require(request.getUserId(), UserMessages.USER_ID_EMPTY);
        require(request.getPassword(), UserMessages.PASSWORD_EMPTY);
        require(request.getUserType(), UserMessages.USER_TYPE_EMPTY);
    }

    /** COUSR02C.cbl:177-213 — User ID first, then the four data fields. */
    public void validateUpdate(String userId, UserUpdateRequest request) {
        require(userId, UserMessages.USER_ID_EMPTY);
        require(request.getFirstName(), UserMessages.FIRST_NAME_EMPTY);
        require(request.getLastName(), UserMessages.LAST_NAME_EMPTY);
        require(request.getPassword(), UserMessages.PASSWORD_EMPTY);
        require(request.getUserType(), UserMessages.USER_TYPE_EMPTY);
    }

    /**
     * The empty-key guard shared by the CU02/CU03 fetch and the CU03 delete
     * (COUSR02C.cbl:143-155, COUSR03C.cbl:142-154, 174-186).
     */
    public void requireUserId(String userId) {
        require(userId, UserMessages.USER_ID_EMPTY);
    }

    private void require(String value, String message) {
        if (UserFields.isBlank(value)) {
            throw new UserValidationException(message);
        }
    }
}
