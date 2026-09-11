package com.carddemo.user.service;

import com.carddemo.common.domain.SecUserRecord;
import com.carddemo.common.repository.SecUserRepository;
import com.carddemo.user.UserMessages;
import com.carddemo.user.domain.UserFields;
import com.carddemo.user.dto.UserActionResponse;
import com.carddemo.user.exception.UserNotFoundException;
import com.carddemo.user.exception.UserStoreException;
import com.carddemo.user.validator.UserValidator;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * CU03 — Delete User (COUSR03C).
 *
 * <p>DELETE-USER-INFO checks the key is present, reads the record and deletes it
 * (cbl:174-192, 267-336). There is no confirmation gate and no protection for
 * the signed-on or last administrator (quirk Q7, FR-UD-14), and the WHEN OTHER
 * arm reports "Unable to Update User..." — the update program's literal, on the
 * delete screen (quirk Q3, FR-UD-10).
 */
@Service
public class UserDeleteService {

    private final SecUserRepository repository;
    private final UserValidator validator;

    public UserDeleteService(SecUserRepository repository, UserValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    @Transactional
    public UserActionResponse delete(String userId) {
        validator.requireUserId(userId);

        String key = UserFields.fit(userId, UserFields.USER_ID_LEN);
        Optional<SecUserRecord> record;
        try {
            record = repository.findById(key);
        } catch (DataAccessException ex) {
            throw new UserStoreException(UserMessages.UNABLE_TO_LOOKUP_USER, ex);
        }
        SecUserRecord found = record.orElseThrow(UserNotFoundException::new);

        try {
            // Flushed here so a failing DELETE reaches the operator as the
            // legacy literal, the way CICS reports RESP to the program.
            repository.delete(found);
            repository.flush();
        } catch (DataAccessException ex) {
            throw new UserStoreException(UserMessages.UNABLE_TO_UPDATE_USER, ex);
        }

        return new UserActionResponse(key, UserMessages.userDeleted(key));
    }
}
