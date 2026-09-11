package com.carddemo.user.service;

import com.carddemo.common.domain.SecUserRecord;
import com.carddemo.common.repository.SecUserRepository;
import com.carddemo.user.dto.UserUpdateRequest;
import com.carddemo.user.exception.UserStoreException;
import com.carddemo.user.validator.UserValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FR-UU-5 / FR-UU-10 — the {@code WHEN OTHER} arms of the CU02 READ and REWRITE
 * (COUSR02C.cbl:347-352, 384-389): "Unable to lookup User..." and
 * "Unable to Update User...".
 */
class UserUpdateServiceMockTest {

    private SecUserRepository repository;
    private UserUpdateService service;

    @BeforeEach
    void setUp() {
        repository = mock(SecUserRepository.class);
        service = new UserUpdateService(repository, new UserValidator());
    }

    private static UserUpdateRequest request() {
        UserUpdateRequest r = new UserUpdateRequest();
        r.setFirstName("LARRY");
        r.setLastName("THOMAS");
        r.setPassword("PASSWORD");
        r.setUserType("U");
        return r;
    }

    private static SecUserRecord stored() {
        SecUserRecord record = new SecUserRecord();
        record.setSecUsrId("USER0001");
        record.setSecUsrFname("LAWRENCE");
        record.setSecUsrLname("THOMAS");
        record.setSecUsrPwd("PASSWORD");
        record.setSecUsrType("U");
        return record;
    }

    @Test
    void frUU5_readFailure_reportsUnableToLookupUser() {
        when(repository.findById("USER0001"))
                .thenThrow(new DataAccessResourceFailureException("USRSEC unavailable"));

        assertThatThrownBy(() -> service.fetch("USER0001"))
                .isInstanceOf(UserStoreException.class)
                .hasMessage("Unable to lookup User...");
    }

    @Test
    void frUU10_rewriteFailure_reportsUnableToUpdateUser() {
        when(repository.findById("USER0001")).thenReturn(Optional.of(stored()));
        when(repository.saveAndFlush(any(SecUserRecord.class)))
                .thenThrow(new DataAccessResourceFailureException("USRSEC unavailable"));

        assertThatThrownBy(() -> service.update("USER0001", request()))
                .isInstanceOf(UserStoreException.class)
                .hasMessage("Unable to Update User...");
    }
}
