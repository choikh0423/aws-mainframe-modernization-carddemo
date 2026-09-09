package com.carddemo.reporting.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-R8 / FR-R9 — the ranges CORPT00C derives from {@code FUNCTION CURRENT-DATE}
 * (cbl:215-253), including the month-end walk that rolls over the year.
 */
class ReportPeriodTest {

    private static ReportPeriodResolver on(String isoDate) {
        return new ReportPeriodResolver(
                Clock.fixed(Instant.parse(isoDate + "T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("FR-R8: Monthly runs from the 1st to the last day of the current month")
    void monthlyCoversTheCurrentMonth() {
        ReportPeriod period = on("2023-07-15").monthly();

        assertThat(period.getReportName()).isEqualTo("Monthly");
        assertThat(period.getStartDate()).hasToString("2023-07-01");
        assertThat(period.getEndDate()).hasToString("2023-07-31");
    }

    @Test
    @DisplayName("FR-R8: a 30-day month ends on the 30th")
    void monthlyHandlesShortMonths() {
        assertThat(on("2023-06-30").monthly().getEndDate()).hasToString("2023-06-30");
    }

    @Test
    @DisplayName("FR-R8: February in a leap year ends on the 29th")
    void monthlyHandlesLeapFebruary() {
        assertThat(on("2024-02-05").monthly().getEndDate()).hasToString("2024-02-29");
    }

    @Test
    @DisplayName("FR-R8: in December the COBOL rolls the year forward before stepping back a day")
    void monthlyRollsTheYearInDecember() {
        ReportPeriod period = on("2023-12-09").monthly();

        assertThat(period.getStartDate()).hasToString("2023-12-01");
        assertThat(period.getEndDate()).hasToString("2023-12-31");
    }

    @Test
    @DisplayName("FR-R9: Yearly runs 01-01 to 12-31 of the current year")
    void yearlyCoversTheCurrentYear() {
        ReportPeriod period = on("2023-07-15").yearly();

        assertThat(period.getReportName()).isEqualTo("Yearly");
        assertThat(period.getStartDate()).hasToString("2023-01-01");
        assertThat(period.getEndDate()).hasToString("2023-12-31");
    }
}
