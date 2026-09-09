package com.carddemo.batch.intcalc;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-I14: Z-GET-DB2-FORMAT-TIMESTAMP, YYYY-MM-DD-HH.MM.SS.hh0000. */
class Db2TimestampTest {

    @Test
    void formatsTheCurrentDateLikeCbact04c() {
        String timestamp = Db2Timestamp.now(
                Clock.fixed(Instant.parse("2022-07-18T09:08:07.650Z"), ZoneId.of("UTC")));

        assertThat(timestamp).isEqualTo("2022-07-18-09.08.07.650000");
        assertThat(timestamp).hasSize(26);
    }

    /** COB-MIL is hundredths of a second, and the last four digits are the literal '0000'. */
    @Test
    void keepsHundredthsOfASecondAndPadsWithFourZeros() {
        String timestamp = Db2Timestamp.now(
                Clock.fixed(Instant.parse("2022-12-31T23:59:59.999Z"), ZoneId.of("UTC")));

        assertThat(timestamp).isEqualTo("2022-12-31-23.59.59.990000");
    }
}
