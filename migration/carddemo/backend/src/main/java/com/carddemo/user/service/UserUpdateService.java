package com.carddemo.user.service;

import com.carddemo.common.domain.SecUserRecord;
import com.carddemo.common.repository.SecUserRepository;
import com.carddemo.user.UserMessages;
import com.carddemo.user.domain.UserFields;
import com.carddemo.user.dto.UserActionResponse;
import com.carddemo.user.dto.UserDetailResponse;
import com.carddemo.user.dto.UserUpdateRequest;
import com.carddemo.user.exception.UserNotFoundException;
import com.carddemo.user.exception.UserNotModifiedException;
import com.carddemo.user.exception.UserStoreException;
import com.carddemo.user.validator.UserValidator;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * CU02 — Update User (COUSR02C), plus the record fetch CU03 shares.
 *
 * <p>{@link #fetch(String)} is PROCESS-ENTER-KEY / READ-USER-SEC-FILE
 * (cbl:143-172, 320-353); {@link #update(String, UserUpdateRequest)} is
 * UPDATE-USER-INFO / UPDATE-USER-SEC-FILE (cbl:177-245, 355-390): the edits run
 * first, then the stored record is re-read and each of the four data fields is
 * compared independently, and the REWRITE only happens when at least one
 * differs — otherwise the screen shows "Please modify to update ...".
 */
@Service
public class UserUpdateService {

    private final SecUserRepository repository;
    private final UserValidator validator;

    public UserUpdateService(SecUserRepository repository, UserValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    /** The CU02/CU03 fetch: FR-UU-2..FR-UU-5, FR-UD-2..FR-UD-5. */
    @Transactional(readOnly = true)
    public UserDetailResponse fetch(String userId) {
        validator.requireUserId(userId);
        return UserDetailResponse.from(read(userId));
    }

    /** CU02 PF5/PF3 save: FR-UU-6..FR-UU-10, FR-UU-16. */
    @Transactional
    public UserActionResponse update(String userId, UserUpdateRequest request) {
        validator.validateUpdate(userId, request);

        SecUserRecord record = read(userId);

        String firstName = UserFields.fit(request.getFirstName(), UserFields.FIRST_NAME_LEN);
        String lastName = UserFields.fit(request.getLastName(), UserFields.LAST_NAME_LEN);
        String password = UserFields.fit(request.getPassword(), UserFields.PASSWORD_LEN);
        String userType = UserFields.fit(request.getUserType(), UserFields.USER_TYPE_LEN);

        boolean modified = false;
        if (!firstName.equals(trim(record.getSecUsrFname()))) {
            record.setSecUsrFname(firstName);
            modified = true;
        }
        if (!lastName.equals(trim(record.getSecUsrLname()))) {
            record.setSecUsrLname(lastName);
            modified = true;
        }
        if (!password.equals(trim(record.getSecUsrPwd()))) {
            record.setSecUsrPwd(password);
            modified = true;
        }
        if (!userType.equals(trim(record.getSecUsrType()))) {
            record.setSecUsrType(userType);
            modified = true;
        }

        if (!modified) {
            throw new UserNotModifiedException();
        }

        try {
            repository.save(record);
        } catch (DataAccessException ex) {
            throw new UserStoreException(UserMessages.UNABLE_TO_UPDATE_USER, ex);
        }

        String key = record.getSecUsrId();
        return new UserActionResponse(key, UserMessages.userUpdated(key));
    }

    private SecUserRecord read(String userId) {
        String key = UserFields.fit(userId, UserFields.USER_ID_LEN);
        Optional<SecUserRecord> record;
        try {
            record = repository.findById(key);
        } catch (DataAccessException ex) {
            throw new UserStoreException(UserMessages.UNABLE_TO_LOOKUP_USER, ex);
        }
        return record.orElseThrow(UserNotFoundException::new);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
