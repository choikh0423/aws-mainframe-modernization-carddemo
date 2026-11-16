package com.carddemo.statements;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatementFormatTest {

    @Test
    void masksAllButTheLastFourDigits() {
        assertThat(StatementFormat.maskCard("9680294154603697")).isEqualTo("************3697");
        assertThat(StatementFormat.maskCard("123")).isEqualTo("123");
        assertThat(StatementFormat.maskCard(null)).isNull();
    }

    @Test
    void padsIdsToTheirPictureLength() {
        assertThat(StatementFormat.accountId(1L)).isEqualTo("00000000001");
        assertThat(StatementFormat.customerId(1L)).isEqualTo("000000001");
    }
}
