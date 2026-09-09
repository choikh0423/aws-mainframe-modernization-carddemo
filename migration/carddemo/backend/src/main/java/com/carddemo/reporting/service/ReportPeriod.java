package com.carddemo.reporting.service;

import java.time.LocalDate;

/**
 * A resolved report selection: the type CORPT00C matched and the inclusive range
 * it moved into {@code WS-START-DATE} / {@code WS-END-DATE} (cbl:60-71).
 */
public final class ReportPeriod {

    private final ReportType type;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public ReportPeriod(ReportType type, LocalDate startDate, LocalDate endDate) {
        this.type = type;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public ReportType getType() { return type; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }

    public String getReportName() { return type.getReportName(); }
}
