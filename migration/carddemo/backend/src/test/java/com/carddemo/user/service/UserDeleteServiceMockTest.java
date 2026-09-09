package com.carddemo.user.service;

import com.carddemo.common.domain.SecUserRecord;
import com.carddemo.common.repository.SecUserRepository;
import com.carddemo.user.exception.UserStoreException;
import com.carddemo.user.validator.UserValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FR-UD-10 — the {@code WHEN OTHER} arm of the CU03 DELETE
 * (COUSR03C.cbl:330-334) reports "Unable to Update User...", the update
 * program's literal (quirk Q3): the wording is preserved, not corrected.
 */
class UserDeleteServiceMockTest {

    private SecUserRepository repository;
    private UserDeleteService service;

    @BeforeEach
    void setUp() {
        repository = mock(SecUserRepository.class);
        service = new UserDeleteService(repository, new UserValidator());
    }

    @Test
    void frUD10_deleteFailure_reportsTheUpdateWording() {
        SecUserRecord record = new SecUserRecord();
        record.setSecUsrId("USER0001");
        when(repository.findById("USER0001")).thenReturn(Optional.of(record));
        Mockito.doThrow(new DataAccessResourceFailureException("USRSEC unavailable"))
                .when(repository).delete(any(SecUserRecord.class));

        assertThatThrownBy(() -> service.delete("USER0001"))
                .isInstanceOf(UserStoreException.class)
                .hasMessage("Unable to Update User...");
    }

    @Test
    void frUD5_readFailure_reportsUnableToLookupUser() {
        when(repository.findById("USER0001"))
                .thenThrow(new DataAccessResourceFailureException("USRSEC unavailable"));

        assertThatThrownBy(() -> service.delete("USER0001"))
                .isInstanceOf(UserStoreException.class)
                .hasMessage("Unable to lookup User...");
    }
}
