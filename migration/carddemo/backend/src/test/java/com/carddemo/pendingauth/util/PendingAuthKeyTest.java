package com.carddemo.pendingauth.util;

import com.carddemo.common.domain.PendingAuthDetailId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-K1..FR-K4: the PAUT9CTS sequence field and its 9s-complement arithmetic.
 */
class PendingAuthKeyTest {

    @Test
    void frK1_externalKeyIsTheTwoComplementedFieldsZeroPadded() {
        assertThat(PendingAuthKey.of(74984, 849999999L)).isEqualTo("74984849999999");
        assertThat(PendingAuthKey.of(new PendingAuthDetailId(1L, 74984, 849999999L)))
                .isEqualTo("74984849999999");
    }

    @Test
    void frK2_keyOrderEqualsSegmentOrder() {
        // A later clock time yields a smaller complement, so ascending key order is
        // most-recent-first, the order GNP PAUTDTL1 returns.
        assertThat(PendingAuthKey.of(74984, 849999999L))
                .isLessThan(PendingAuthKey.of(74984, 909999999L));
    }

    @Test
    void frK3_onlyFourteenDigitKeysAreUsable() {
        assertThat(PendingAuthKey.isValid("74984849999999")).isTrue();
        assertThat(PendingAuthKey.isValid(" 74984849999999 ")).isTrue();
        assertThat(PendingAuthKey.isValid("7498484999999")).isFalse();
        assertThat(PendingAuthKey.isValid("7498484999999X")).isFalse();
        assertThat(PendingAuthKey.isValid(null)).isFalse();
        assertThat(PendingAuthKey.date9c("74984849999999")).isEqualTo(74984);
        assertThat(PendingAuthKey.time9c("74984849999999")).isEqualTo(849999999L);
    }

    @Test
    void frK4_newKeyComplementsTheCicsClock() {
        LocalDateTime now = LocalDateTime.of(2025, 1, 15, 15, 0, 0, 250_000_000);
        PendingAuthDetailId id = PendingAuthKey.newKey(1L, now);

        assertThat(id.getPaAcctId()).isEqualTo(1L);
        assertThat(id.getPaAuthDate9c()).isEqualTo(99999 - 25015);
        assertThat(id.getPaAuthTime9c()).isEqualTo(999999999L - 150000250L);
        assertThat(PendingAuthKey.yyddd(LocalDate.of(2025, 1, 15))).isEqualTo(25015);
        assertThat(PendingAuthKey.invertDate(25015)).isEqualTo(74984);
        assertThat(PendingAuthKey.invertTime(150000250L)).isEqualTo(849999749L);
    }
}
