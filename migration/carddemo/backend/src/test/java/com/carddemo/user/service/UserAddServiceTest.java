package com.carddemo.user.service;

import com.carddemo.common.domain.SecUserRecord;
import com.carddemo.common.repository.SecUserRepository;
import com.carddemo.user.dto.UserActionResponse;
import com.carddemo.user.dto.UserAddRequest;
import com.carddemo.user.exception.DuplicateUserIdException;
import com.carddemo.user.exception.UserValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-UA-2..FR-UA-6, FR-UA-10..FR-UA-12 — CU01 WRITE-USER-SEC-FILE against the
 * seeded H2 USRSEC. Rolled back so the ten fixture records stay intact for the
 * other streams sharing the context.
 */
@SpringBootTest
@Transactional
class UserAddServiceTest {

    @Autowired
    private UserAddService service;

    @Autowired
    private SecUserRepository repository;

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
    void frUA3_validRequest_writesTheRecordAndConfirms() {
        UserActionResponse response = service.add(request());

        assertThat(response.getUserId()).isEqualTo("NEWUSR01");
        assertThat(response.getMessage()).isEqualTo("User NEWUSR01 has been added ...");

        SecUserRecord stored = repository.findById("NEWUSR01").orElseThrow();
        assertThat(stored.getSecUsrFname()).isEqualTo("JOHN");
        assertThat(stored.getSecUsrLname()).isEqualTo("DOE");
        assertThat(stored.getSecUsrPwd()).isEqualTo("PASSWD01");
        assertThat(stored.getSecUsrType()).isEqualTo("U");
    }

    @Test
    void frUA5_existingId_isRejectedAsDuplicate() {
        UserAddRequest r = request();
        r.setUserId("ADMIN001");

        assertThatThrownBy(() -> service.add(r))
                .isInstanceOf(DuplicateUserIdException.class)
                .hasMessage("User ID already exist...");

        assertThat(repository.findById("ADMIN001").orElseThrow().getSecUsrFname())
                .isEqualTo("MARGARET");
    }

    @Test
    void frUA2_theFirstFailingEditStopsTheWrite() {
        UserAddRequest r = request();
        r.setFirstName(" ");

        assertThatThrownBy(() -> service.add(r))
                .isInstanceOf(UserValidationException.class)
                .hasMessage("First Name can NOT be empty...");

        assertThat(repository.findById("NEWUSR01")).isEmpty();
    }

    @Test
    void frUA10_quirkQ1_anArbitraryUserTypeIsStored() {
        UserAddRequest r = request();
        r.setUserId("NEWUSR02");
        r.setUserType("X");

        service.add(r);

        assertThat(repository.findById("NEWUSR02").orElseThrow().getSecUsrType()).isEqualTo("X");
    }

    @Test
    void frUA12_overlongFieldsAreTruncatedToThePicture() {
        UserAddRequest r = request();
        r.setUserId("NEWUSR03EXTRA");
        r.setFirstName("A".repeat(30));
        r.setPassword("PASSWORD123");
        r.setUserType("UA");

        UserActionResponse response = service.add(r);

        assertThat(response.getUserId()).isEqualTo("NEWUSR03");
        SecUserRecord stored = repository.findById("NEWUSR03").orElseThrow();
        assertThat(stored.getSecUsrFname()).hasSize(20);
        assertThat(stored.getSecUsrPwd()).isEqualTo("PASSWORD");
        assertThat(stored.getSecUsrType()).isEqualTo("U");
    }
}
