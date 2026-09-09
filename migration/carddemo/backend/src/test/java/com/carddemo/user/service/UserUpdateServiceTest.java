package com.carddemo.user.service;

import com.carddemo.common.repository.SecUserRepository;
import com.carddemo.user.dto.UserActionResponse;
import com.carddemo.user.dto.UserDetailResponse;
import com.carddemo.user.dto.UserUpdateRequest;
import com.carddemo.user.exception.UserNotFoundException;
import com.carddemo.user.exception.UserNotModifiedException;
import com.carddemo.user.exception.UserValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-UU-2..FR-UU-10, FR-UU-16 — CU02 fetch and rewrite against the seeded H2
 * USRSEC, rolled back after each test.
 */
@SpringBootTest
@Transactional
class UserUpdateServiceTest {

    @Autowired
    private UserUpdateService service;

    @Autowired
    private SecUserRepository repository;

    private static UserUpdateRequest request(String fn, String ln, String pwd, String type) {
        UserUpdateRequest r = new UserUpdateRequest();
        r.setFirstName(fn);
        r.setLastName(ln);
        r.setPassword(pwd);
        r.setUserType(type);
        return r;
    }

    @Test
    void frUU3_fetch_returnsTheStoredRecord() {
        UserDetailResponse detail = service.fetch("USER0001");

        assertThat(detail.getUserId()).isEqualTo("USER0001");
        assertThat(detail.getFirstName()).isEqualTo("LAWRENCE");
        assertThat(detail.getLastName()).isEqualTo("THOMAS");
        assertThat(detail.getPassword()).isEqualTo("PASSWORD");
        assertThat(detail.getUserType()).isEqualTo("U");
    }

    @Test
    void frUU4_fetchOfAnUnknownId_reportsUserIdNotFound() {
        assertThatThrownBy(() -> service.fetch("NOSUCH01"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User ID NOT found...");
    }

    @Test
    void frUU2_fetchWithABlankKey_reportsUserIdCanNotBeEmpty() {
        assertThatThrownBy(() -> service.fetch("   "))
                .isInstanceOf(UserValidationException.class)
                .hasMessage("User ID can NOT be empty...");
    }

    @Test
    void frUU7_changedFields_areRewrittenAndConfirmed() {
        UserActionResponse response =
                service.update("USER0001", request("LARRY", "THOMAS", "PASSWORD", "U"));

        assertThat(response.getMessage()).isEqualTo("User USER0001 has been updated ...");
        assertThat(repository.findById("USER0001").orElseThrow().getSecUsrFname())
                .isEqualTo("LARRY");
    }

    @Test
    void frUU8_noChange_reportsPleaseModifyToUpdate() {
        assertThatThrownBy(() ->
                service.update("USER0002", request("AJITH", "KUMAR", "PASSWORD", "U")))
                .isInstanceOf(UserNotModifiedException.class)
                .hasMessage("Please modify to update ...");
    }

    @Test
    void frUU7_everyEditableFieldIsComparedIndependently() {
        service.update("USER0003", request("LAURITZ", "ALME", "NEWPASS1", "A"));

        var stored = repository.findById("USER0003").orElseThrow();
        assertThat(stored.getSecUsrPwd()).isEqualTo("NEWPASS1");
        assertThat(stored.getSecUsrType()).isEqualTo("A");
    }

    @Test
    void frUU16_updateOfAnUnknownId_reportsUserIdNotFound_andCreatesNothing() {
        assertThatThrownBy(() ->
                service.update("NOSUCH01", request("A", "B", "C", "U")))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User ID NOT found...");

        assertThat(repository.findById("NOSUCH01")).isEmpty();
    }

    @Test
    void frUU15_theUserIdIsNotAnEditableField() {
        service.update("USER0004", request("ERICA", "SANTOS", "NEWPASS4", "U"));

        assertThat(repository.findById("USER0004")).isPresent();
        assertThat(repository.count()).isEqualTo(10);
    }

    @Test
    void frUU17_quirkQ1_anArbitraryUserTypeIsStored() {
        service.update("USER0005", request("MICHELE", "MOORE", "PASSWORD", "x"));

        assertThat(repository.findById("USER0005").orElseThrow().getSecUsrType())
                .isEqualTo("x");
    }

    @Test
    void frUU6_editsRunBeforeTheRead() {
        assertThatThrownBy(() ->
                service.update("USER0001", request("", "THOMAS", "PASSWORD", "U")))
                .isInstanceOf(UserValidationException.class)
                .hasMessage("First Name can NOT be empty...");
    }
}
