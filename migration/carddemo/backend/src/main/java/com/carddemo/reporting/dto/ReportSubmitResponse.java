package com.carddemo.reporting.dto;

/**
 * The state of map CORPT0A after a CR00 ENTER that did not end in an error:
 * either the job was submitted (FR-R36) or the operator answered N to the
 * confirmation and the screen was simply cleared (FR-R31).
 *
 * <p>{@code fieldsCleared} is always true here because both paths run
 * INITIALIZE-ALL-FIELDS (cbl:447, 481); {@code cursor} is where CORPT00C left
 * the cursor.
 */
public class ReportSubmitResponse {

    private final boolean submitted;
    private final String reportName;
    private final String startDate;
    private final String endDate;
    private final String message;
    private final String cursor;

    public ReportSubmitResponse(boolean submitted, String reportName, String startDate,
                                String endDate, String message, String cursor) {
        this.submitted = submitted;
        this.reportName = reportName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.message = message;
        this.cursor = cursor;
    }

    public boolean isSubmitted() { return submitted; }
    public String getReportName() { return reportName; }
    public String getStartDate() { return startDate; }
    public String getEndDate() { return endDate; }
    public String getMessage() { return message; }
    public String getCursor() { return cursor; }

    /** Both success paths clear every input field (cbl:633-646). */
    public boolean isFieldsCleared() { return true; }
}
