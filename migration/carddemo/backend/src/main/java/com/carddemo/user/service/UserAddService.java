package com.carddemo.user.service;

import com.carddemo.common.domain.SecUserRecord;
import com.carddemo.common.repository.SecUserRepository;
import com.carddemo.user.UserMessages;
import com.carddemo.user.domain.UserFields;
import com.carddemo.user.dto.UserActionResponse;
import com.carddemo.user.dto.UserAddRequest;
import com.carddemo.user.exception.DuplicateUserIdException;
import com.carddemo.user.exception.UserStoreException;
import com.carddemo.user.validator.UserValidator;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU01 — Add User (COUSR01C).
 *
 * <p>PROCESS-ENTER-KEY runs the five required-empty edits, moves the screen
 * fields into SEC-USER-DATA and issues {@code EXEC CICS WRITE}
 * (cbl:115-160, 238-274). The three outcomes are reproduced here: NORMAL ->
 * the green "User <id> has been added ...", DUPKEY/DUPREC ->
 * "User ID already exist...", WHEN OTHER -> "Unable to Add User...".
 *
 * <p>{@code EXEC CICS WRITE} reports its outcome to the program that issued it,
 * so the write is flushed inside this block rather than at commit: otherwise a
 * constraint violation raised after the method returns would escape the
 * translation and lose the literal the screen has to show.
 */
@Service
public class UserAddService {

    private final SecUserRepository repository;
    private final UserValidator validator;

    public UserAddService(SecUserRepository repository, UserValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    @Transactional
    public UserActionResponse add(UserAddRequest request) {
        validator.validateAdd(request);

        String userId = UserFields.fit(request.getUserId(), UserFields.USER_ID_LEN);
        try {
            if (repository.existsById(userId)) {
                throw new DuplicateUserIdException();
            }

            SecUserRecord record = new SecUserRecord();
            record.setSecUsrId(userId);
            record.setSecUsrFname(UserFields.fit(request.getFirstName(), UserFields.FIRST_NAME_LEN));
            record.setSecUsrLname(UserFields.fit(request.getLastName(), UserFields.LAST_NAME_LEN));
            record.setSecUsrPwd(UserFields.fit(request.getPassword(), UserFields.PASSWORD_LEN));
            record.setSecUsrType(UserFields.fit(request.getUserType(), UserFields.USER_TYPE_LEN));
            repository.saveAndFlush(record);
        } catch (DataIntegrityViolationException ex) {
            // The DUPREC the WRITE reports when the key was taken between the
            // existsById probe and the write itself.
            throw new DuplicateUserIdException();
        } catch (DataAccessException ex) {
            throw new UserStoreException(UserMessages.UNABLE_TO_ADD_USER, ex);
        }

        return new UserActionResponse(userId, UserMessages.userAdded(userId));
    }
}
