package com.carddemo.billpayment.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CobolRecordsTest {

    @Test
    void overpunchRoundTrip() {
        assertThat(CobolRecords.zoned("00000001940{", 2)).isEqualByComparingTo("194.00");
        assertThat(CobolRecords.zoned("0000000194J", 2)).isEqualByComparingTo("-19.41");
        assertThat(CobolRecords.zoned(new BigDecimal("-19.41"), 11, 2)).isEqualTo("0000000194J");
        assertThat(CobolRecords.zoned(new BigDecimal("194.00"), 12, 2)).isEqualTo("00000001940{");
    }

    @Test
    void accountRecordRoundTrip() {
        String rec = CobolRecords.pad("00000000001Y00000001940{00000020200{00000010200{2014-11-202025-05-20"
                + "2025-05-2000000000000{00000000000{A000000000", 300);
        assertThat(CobolRecords.formatAccount(CobolRecords.parseAccount(rec))).isEqualTo(rec);
    }
}
