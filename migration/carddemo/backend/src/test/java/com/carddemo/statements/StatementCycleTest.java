package com.carddemo.statements;

import com.carddemo.common.batch.AbendException;
import com.carddemo.common.batch.AbendService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatementCycleTest {

    private final StatementCycle cycle = new StatementCycle(new AbendService(),
            Clock.fixed(Instant.parse("2026-11-30T23:59:59Z"), ZoneOffset.UTC));

    @Test
    void omittedIsTheCurrentMonth() {
        assertThat(cycle.resolve(null)).isEqualTo("2026-11");
    }

    @Test
    void aValidCycleIsKept() {
        assertThat(cycle.resolve("2026-10")).isEqualTo("2026-10");
        assertThat(cycle.resolve("1999-12")).isEqualTo("1999-12");
    }

    @Test
    void aBadCycleAbends() {
        assertThatThrownBy(() -> cycle.resolve("2026-13"))
                .isInstanceOf(AbendException.class)
                .satisfies(e -> {
                    var data = ((AbendException) e).getAbendData();
                    assertThat(data.abendCulprit()).isEqualTo("CREASTMT");
                    assertThat(data.abendMsg()).isEqualTo("INVALID CYCLE PARAMETER '2026-13' - EXPECTED YYYY-MM");
                });
    }
}
