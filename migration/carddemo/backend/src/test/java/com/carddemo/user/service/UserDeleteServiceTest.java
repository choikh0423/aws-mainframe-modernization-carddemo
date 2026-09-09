package com.carddemo.user.service;

import com.carddemo.common.repository.SecUserRepository;
import com.carddemo.user.dto.UserActionResponse;
import com.carddemo.user.exception.UserNotFoundException;
import com.carddemo.user.exception.UserValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-UD-6..FR-UD-9, FR-UD-14 — CU03 DELETE-USER-INFO against the seeded H2
 * USRSEC, rolled back so the fixture survives for the other streams.
 */
@SpringBootTest
@Transactional
class UserDeleteServiceTest {

    @Autowired
    private UserDeleteService service;

    @Autowired
    private SecUserRepository repository;

    @Test
    void frUD7_existingUser_isDeletedAndConfirmed() {
        UserActionResponse response = service.delete("USER0005");

        assertThat(response.getMessage()).isEqualTo("User USER0005 has been deleted ...");
        assertThat(repository.findById("USER0005")).isEmpty();
    }

    @Test
    void frUD9_unknownId_reportsUserIdNotFound() {
        assertThatThrownBy(() -> service.delete("NOSUCH01"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User ID NOT found...");
    }

    @Test
    void frUD6_blankKey_reportsUserIdCanNotBeEmpty() {
        assertThatThrownBy(() -> service.delete(" "))
                .isInstanceOf(UserValidationException.class)
                .hasMessage("User ID can NOT be empty...");
    }

    @Test
    void frUD14_quirkQ7_anAdministratorCanBeDeletedWithoutAGuard() {
        UserActionResponse response = service.delete("ADMIN001");

        assertThat(response.getMessage()).isEqualTo("User ADMIN001 has been deleted ...");
        assertThat(repository.findById("ADMIN001")).isEmpty();
    }
}
