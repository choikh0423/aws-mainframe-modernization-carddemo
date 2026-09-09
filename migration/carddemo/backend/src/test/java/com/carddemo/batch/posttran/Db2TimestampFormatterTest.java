package com.carddemo.batch.posttran;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-S11-011 - Z-GET-DB2-FORMAT-TIMESTAMP (CBTRN02C.cbl:692-705). */
class Db2TimestampFormatterTest {

    @Test
    void formatsCurrentDateLikeZGetDb2FormatTimestamp() {
        String formatted = Db2TimestampFormatter.format(
                LocalDateTime.of(2024, 3, 7, 9, 4, 5, 120_000_000));

        assertThat(formatted).isEqualTo("2024-03-07-09.04.05.120000");
        assertThat(formatted).hasSize(26);
    }

    @Test
    void padsEveryComponentAndAlwaysEndsWithTheFourZeroesOfDb2Rest() {
        assertThat(Db2TimestampFormatter.format(LocalDateTime.of(2024, 12, 31, 23, 59, 59, 999_000_000)))
                .isEqualTo("2024-12-31-23.59.59.990000");
        assertThat(Db2TimestampFormatter.format(LocalDateTime.of(2024, 1, 1, 0, 0, 0, 0)))
                .isEqualTo("2024-01-01-00.00.00.000000");
    }

    @Test
    void stampsTheCurrentTimeOfItsClock() {
        Clock clock = Clock.fixed(Instant.parse("2024-06-01T10:11:12.34Z"), ZoneId.of("UTC"));

        assertThat(new Db2TimestampFormatter(clock).now()).isEqualTo("2024-06-01-10.11.12.340000");
    }
}
