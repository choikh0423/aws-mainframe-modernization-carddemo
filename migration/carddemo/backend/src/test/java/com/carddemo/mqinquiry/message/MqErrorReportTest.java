package com.carddemo.mqinquiry.message;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-MQI-004: the MQ-ERR-DISPLAY layout put on CARD.DEMO.ERROR. */
class MqErrorReportTest {

    @Test
    void laysTheFieldsOutAtTheCopybookOffsets() {
        String report = new MqErrorReport("CICS RETREIVE", "MQPUT ERR", 8, 2033, "CARD.DEMO.ERROR").render();

        assertThat(report).hasSize(1000);
        assertThat(report.substring(0, 25)).isEqualTo("CICS RETREIVE            ");
        assertThat(report.substring(25, 27)).isEqualTo("  ");
        assertThat(report.substring(27, 52)).isEqualTo("MQPUT ERR                ");
        assertThat(report.substring(52, 54)).isEqualTo("  ");
        assertThat(report.substring(54, 56)).isEqualTo("08");
        assertThat(report.substring(58, 63)).isEqualTo("02033");
        assertThat(report.substring(65, 113)).isEqualTo("CARD.DEMO.ERROR" + " ".repeat(33));
        assertThat(report.substring(113)).isBlank();
    }

    @Test
    void truncatesTheReturnMessageAndTheConditionCodeTheWayTheirPicturesDo() {
        String report = new MqErrorReport("", "ERROR WHILE READING ACCTFILE", 120, 0,
                "CARDDEMO.REQUEST.QUEUE").render();

        assertThat(report.substring(0, 25)).isBlank();
        assertThat(report.substring(27, 52)).isEqualTo("ERROR WHILE READING ACCTF");
        assertThat(report.substring(54, 56)).isEqualTo("20");
        assertThat(report.substring(58, 63)).isEqualTo("00000");
    }
}
