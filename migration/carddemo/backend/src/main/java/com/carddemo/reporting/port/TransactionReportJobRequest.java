package com.carddemo.reporting.port;

import java.time.LocalDate;
import java.util.Objects;

/**
 * What CR00 hands to the TRANREPT job: the report CORPT00C named
 * ({@code Monthly} / {@code Yearly} / {@code Custom}, cbl:214/240/433) and the
 * inclusive date range that reached {@code PARM-START-DATE} /
 * {@code PARM-END-DATE} in the submitted deck (cbl:104-121).
 *
 * <p>The range is exactly what the screen produced: CORPT00C never checks that
 * the end date follows the start date (FR-R27), so neither does this port.
 */
public final class TransactionReportJobRequest {

    private final String reportName;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public TransactionReportJobRequest(String reportName, LocalDate startDate, LocalDate endDate) {
        this.reportName = Objects.requireNonNull(reportName, "reportName");
        this.startDate = Objects.requireNonNull(startDate, "startDate");
        this.endDate = Objects.requireNonNull(endDate, "endDate");
    }

    public String getReportName() { return reportName; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }

    /** {@code PARM-START-DATE} in the YYYY-MM-DD mask CORPT00C builds (cbl:60-65, 72). */
    public String getStartDateText() { return startDate.toString(); }

    /** {@code PARM-END-DATE} in the same mask (cbl:66-71). */
    public String getEndDateText() { return endDate.toString(); }

    @Override
    public String toString() {
        return "TRANREPT[" + reportName + " " + getStartDateText() + ".." + getEndDateText() + "]";
    }
}
