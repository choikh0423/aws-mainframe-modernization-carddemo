package com.carddemo.user.validator;

import com.carddemo.user.dto.UserAddRequest;
import com.carddemo.user.dto.UserUpdateRequest;
import com.carddemo.user.exception.UserValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every branch of the S-05 field edits.
 *
 * FR-UA-2, FR-UA-10, FR-UA-11 (COUSR01C.cbl:115-152);
 * FR-UU-6 (COUSR02C.cbl:177-213);
 * FR-UU-2 / FR-UD-2 / FR-UD-6 empty-key guards (COUSR02C.cbl:143-155, COUSR03C.cbl:142-154).
 */
class UserValidatorTest {

    private final UserValidator validator = new UserValidator();

    private static UserAddRequest add(String fn, String ln, String id, String pwd, String type) {
        UserAddRequest r = new UserAddRequest();
        r.setFirstName(fn);
        r.setLastName(ln);
        r.setUserId(id);
        r.setPassword(pwd);
        r.setUserType(type);
        return r;
    }

    private static UserUpdateRequest upd(String fn, String ln, String pwd, String type) {
        UserUpdateRequest r = new UserUpdateRequest();
        r.setFirstName(fn);
        r.setLastName(ln);
        r.setPassword(pwd);
        r.setUserType(type);
        return r;
    }

    // --- FR-UA-2: the five add edits, in COBOL order ---

    @Test
    void frUA2_missingFirstName_reportsFirstNameFirst() {
        assertThatThrownBy(() -> validator.validateAdd(add("", "", "", "", "")))
                .isInstanceOf(UserValidationException.class)
                .hasMessage("First Name can NOT be empty...");
    }

    @Test
    void frUA2_missingLastName_reportedAfterFirstName() {
        assertThatThrownBy(() -> validator.validateAdd(add("JOHN", "  ", "", "", "")))
                .hasMessage("Last Name can NOT be empty...");
    }

    @Test
    void frUA2_missingUserId_reportedAfterLastName() {
        assertThatThrownBy(() -> validator.validateAdd(add("JOHN", "DOE", null, "", "")))
                .hasMessage("User ID can NOT be empty...");
    }

    @Test
    void frUA2_missingPassword_reportedAfterUserId() {
        assertThatThrownBy(() -> validator.validateAdd(add("JOHN", "DOE", "NEWUSR01", "", "")))
                .hasMessage("Password can NOT be empty...");
    }

    @Test
    void frUA2_missingUserType_reportedLast() {
        assertThatThrownBy(() -> validator.validateAdd(add("JOHN", "DOE", "NEWUSR01", "PASSWORD", " ")))
                .hasMessage("User Type can NOT be empty...");
    }

    @Test
    void frUA2_allFieldsPresent_passes() {
        assertThatCode(() -> validator.validateAdd(add("JOHN", "DOE", "NEWUSR01", "PASSWORD", "U")))
                .doesNotThrowAnyException();
    }

    // --- FR-UA-10 / FR-UA-11 / FR-UU-17: the edits the legacy program does NOT do ---

    @Test
    void frUA10_anyNonBlankUserTypeIsAccepted_noDomainCheck() {
        assertThatCode(() -> validator.validateAdd(add("JOHN", "DOE", "NEWUSR01", "PASSWORD", "X")))
                .doesNotThrowAnyException();
    }

    @Test
    void frUA11_shortIdAndWeakPasswordAreAccepted_noFormatCheck() {
        assertThatCode(() -> validator.validateAdd(add("J", "D", "A", "1", "A")))
                .doesNotThrowAnyException();
    }

    // --- FR-UU-6: the update edits, in COBOL order (User ID first) ---

    @Test
    void frUU6_missingUserId_reportedFirst() {
        assertThatThrownBy(() -> validator.validateUpdate("  ", upd("", "", "", "")))
                .hasMessage("User ID can NOT be empty...");
    }

    @Test
    void frUU6_updateEditsFollowFirstLastPasswordType() {
        assertThatThrownBy(() -> validator.validateUpdate("USER0001", upd("", "", "", "")))
                .hasMessage("First Name can NOT be empty...");
        assertThatThrownBy(() -> validator.validateUpdate("USER0001", upd("A", "", "", "")))
                .hasMessage("Last Name can NOT be empty...");
        assertThatThrownBy(() -> validator.validateUpdate("USER0001", upd("A", "B", "", "")))
                .hasMessage("Password can NOT be empty...");
        assertThatThrownBy(() -> validator.validateUpdate("USER0001", upd("A", "B", "C", "")))
                .hasMessage("User Type can NOT be empty...");
        assertThatCode(() -> validator.validateUpdate("USER0001", upd("A", "B", "C", "D")))
                .doesNotThrowAnyException();
    }

    // --- FR-UD-2: the CU02/CU03 empty-key guard ---

    @Test
    void frUD2_blankKeyIsRejected() {
        assertThatThrownBy(() -> validator.requireUserId(null))
                .hasMessage("User ID can NOT be empty...");
        assertThatThrownBy(() -> validator.requireUserId("   "))
                .hasMessage("User ID can NOT be empty...");
        assertThatCode(() -> validator.requireUserId("USER0001")).doesNotThrowAnyException();
    }
}
