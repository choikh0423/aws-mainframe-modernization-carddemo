package com.carddemo.user.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-US-2 — COBOL {@code MOVE} semantics into the USRSEC pictures
 * (app/cpy/CSUSR01Y.cpy:17-23): over-long values are truncated to the field
 * width and trailing blanks are not significant.
 */
class UserFieldsTest {

    @Test
    void frUS2_valueLongerThanThePictureIsTruncated() {
        assertThat(UserFields.fit("ABCDEFGHIJ", UserFields.USER_ID_LEN)).isEqualTo("ABCDEFGH");
        assertThat(UserFields.fit("AU", UserFields.USER_TYPE_LEN)).isEqualTo("A");
        assertThat(UserFields.fit("A".repeat(25), UserFields.FIRST_NAME_LEN)).hasSize(20);
    }

    @Test
    void frUS2_paddingIsNotSignificant() {
        assertThat(UserFields.fit("  USER0001  ", UserFields.USER_ID_LEN)).isEqualTo("USER0001");
        assertThat(UserFields.fit(null, UserFields.USER_ID_LEN)).isEmpty();
    }

    @Test
    void frUS2_spacesOrLowValuesAreBlank() {
        assertThat(UserFields.isBlank(null)).isTrue();
        assertThat(UserFields.isBlank("   ")).isTrue();
        assertThat(UserFields.isBlank(" A ")).isFalse();
    }
}
