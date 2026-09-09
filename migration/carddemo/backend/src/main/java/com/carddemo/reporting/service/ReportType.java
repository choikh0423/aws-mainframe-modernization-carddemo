package com.carddemo.reporting.service;

/**
 * The three report selections on map CORPT0A, each carrying the name CORPT00C
 * moves into {@code WS-REPORT-NAME} and then prints in the confirmation and
 * success messages (cbl:214, 240, 433).
 */
public enum ReportType {

    MONTHLY("Monthly"),
    YEARLY("Yearly"),
    CUSTOM("Custom");

    private final String reportName;

    ReportType(String reportName) {
        this.reportName = reportName;
    }

    /** {@code WS-REPORT-NAME}, as it appears in the screen messages. */
    public String getReportName() {
        return reportName;
    }
}
