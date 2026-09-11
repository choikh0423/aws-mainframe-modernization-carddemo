package com.carddemo.user.service;

import com.carddemo.common.domain.SecUserRecord;
import com.carddemo.common.repository.SecUserRepository;
import com.carddemo.user.dto.UserAddRequest;
import com.carddemo.user.exception.DuplicateUserIdException;
import com.carddemo.user.exception.UserStoreException;
import com.carddemo.user.validator.UserValidator;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FR-UA-6 — the {@code WHEN OTHER} arm of the CU01 WRITE
 * (COUSR01C.cbl:267-272): "Unable to Add User...". Pure Mockito so the failure
 * can be forced without disturbing the shared H2 seed. The write is flushed
 * inside the service, so both arms are reachable from the mock.
 */
class UserAddServiceMockTest {

    private SecUserRepository repository;
    private UserAddService service;

    @BeforeEach
    void setUp() {
        repository = mock(SecUserRepository.class);
        service = new UserAddService(repository, new UserValidator());
    }

    private static UserAddRequest request() {
        UserAddRequest r = new UserAddRequest();
        r.setFirstName("JOHN");
        r.setLastName("DOE");
        r.setUserId("NEWUSR01");
        r.setPassword("PASSWD01");
        r.setUserType("U");
        return r;
    }

    @Test
    void frUA6_writeFailure_reportsUnableToAddUser() {
        when(repository.existsById("NEWUSR01")).thenReturn(false);
        when(repository.saveAndFlush(any(SecUserRecord.class)))
                .thenThrow(new DataAccessResourceFailureException("USRSEC unavailable"));

        assertThatThrownBy(() -> service.add(request()))
                .isInstanceOf(UserStoreException.class)
                .hasMessage("Unable to Add User...");
    }

    @Test
    void frUA5_constraintViolationOnTheWrite_reportsUserIdAlreadyExist() {
        when(repository.existsById("NEWUSR01")).thenReturn(false);
        when(repository.saveAndFlush(any(SecUserRecord.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.add(request()))
                .isInstanceOf(DuplicateUserIdException.class)
                .hasMessage("User ID already exist...");
    }
}
