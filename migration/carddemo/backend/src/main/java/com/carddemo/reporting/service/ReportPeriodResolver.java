package com.carddemo.reporting.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/**
 * The date ranges CORPT00C derives from {@code FUNCTION CURRENT-DATE} for the
 * two fixed report types.
 *
 * <p>Monthly (cbl:215-236): the first of the current month, to the day before the
 * first of the next month — the COBOL walks the month forward, rolls the year at
 * 12 and subtracts one from the integer date, which is the last day of the
 * current month.
 *
 * <p>Yearly (cbl:241-253): {@code YYYY-01-01} to {@code YYYY-12-31}.
 */
@Service
public class ReportPeriodResolver {

    private final Clock clock;

    public ReportPeriodResolver() {
        this(Clock.systemDefaultZone());
    }

    /** Test seam standing in for {@code FUNCTION CURRENT-DATE}. */
    public ReportPeriodResolver(Clock clock) {
        this.clock = clock;
    }

    public ReportPeriod monthly() {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = today.withDayOfMonth(1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        return new ReportPeriod(ReportType.MONTHLY, start, end);
    }

    public ReportPeriod yearly() {
        LocalDate today = LocalDate.now(clock);
        return new ReportPeriod(ReportType.YEARLY,
                LocalDate.of(today.getYear(), 1, 1),
                LocalDate.of(today.getYear(), 12, 31));
    }
}
