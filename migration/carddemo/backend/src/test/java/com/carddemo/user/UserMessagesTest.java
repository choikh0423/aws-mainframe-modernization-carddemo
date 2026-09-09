package com.carddemo.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-US-3 — the exact literals, including the two source quirks that must not be
 * "fixed": the {@code DELIMITED BY SPACE} truncation of the id inside the
 * confirmation (quirk Q12) and CU03's use of the update program's failure text
 * (quirk Q3, COUSR03C.cbl:330-334).
 */
class UserMessagesTest {

    @Test
    void frUS3_confirmationsQuoteTheIdVerbatim() {
        assertThat(UserMessages.userAdded("NEWUSR01")).isEqualTo("User NEWUSR01 has been added ...");
        assertThat(UserMessages.userUpdated("USER0001")).isEqualTo("User USER0001 has been updated ...");
        assertThat(UserMessages.userDeleted("USER0005")).isEqualTo("User USER0005 has been deleted ...");
    }

    @Test
    void frUS3_quirkQ12_idIsDelimitedByTheFirstSpace() {
        assertThat(UserMessages.userAdded("USR 0001")).isEqualTo("User USR has been added ...");
        assertThat(UserMessages.userDeleted(" LEAD")).isEqualTo("User  has been deleted ...");
    }

    @Test
    void frUD10_quirkQ3_deleteFailureKeepsTheUpdateWording() {
        assertThat(UserMessages.UNABLE_TO_UPDATE_USER).isEqualTo("Unable to Update User...");
    }

    @Test
    void frUS3_boundaryAndSelectionLiteralsAreVerbatim() {
        assertThat(UserMessages.INVALID_SELECTION).isEqualTo("Invalid selection. Valid values are U and D");
        assertThat(UserMessages.ALREADY_AT_TOP).isEqualTo("You are already at the top of the page...");
        assertThat(UserMessages.ALREADY_AT_BOTTOM).isEqualTo("You are already at the bottom of the page...");
        assertThat(UserMessages.AT_TOP_OF_PAGE).isEqualTo("You are at the top of the page...");
        assertThat(UserMessages.REACHED_BOTTOM).isEqualTo("You have reached the bottom of the page...");
        assertThat(UserMessages.REACHED_TOP).isEqualTo("You have reached the top of the page...");
        assertThat(UserMessages.PRESS_PF5_TO_SAVE).isEqualTo("Press PF5 key to save your updates ...");
        assertThat(UserMessages.PRESS_PF5_TO_DELETE).isEqualTo("Press PF5 key to delete this user ...");
        assertThat(UserMessages.PLEASE_MODIFY_TO_UPDATE).isEqualTo("Please modify to update ...");
        assertThat(UserMessages.USER_ID_ALREADY_EXIST).isEqualTo("User ID already exist...");
        assertThat(UserMessages.UNABLE_TO_ADD_USER).isEqualTo("Unable to Add User...");
        assertThat(UserMessages.USER_ID_NOT_FOUND).isEqualTo("User ID NOT found...");
        assertThat(UserMessages.UNABLE_TO_LOOKUP_USER).isEqualTo("Unable to lookup User...");
    }
}
