package com.carddemo.user.service;

import com.carddemo.user.exception.UserStoreException;
import com.carddemo.user.repository.UserBrowseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FR-UL-8 — the {@code WHEN OTHER} arm of STARTBR/READNEXT
 * (COUSR00C.cbl:608-613): any non-NORMAL browse response paints
 * "Unable to lookup User...".
 */
class UserListServiceMockTest {

    @Test
    void frUL8_browseFailure_reportsUnableToLookupUser() {
        UserBrowseRepository repository = mock(UserBrowseRepository.class);
        when(repository.findByOrderBySecUsrIdAsc(any(Pageable.class)))
                .thenThrow(new DataAccessResourceFailureException("USRSEC unavailable"));

        assertThatThrownBy(() -> new UserListService(repository).list(null, null))
                .isInstanceOf(UserStoreException.class)
                .hasMessage("Unable to lookup User...");
    }
}
