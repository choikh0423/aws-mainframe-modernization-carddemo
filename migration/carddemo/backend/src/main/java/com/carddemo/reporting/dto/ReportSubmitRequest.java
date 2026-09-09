package com.carddemo.reporting.dto;

/**
 * The CR00 screen fields exactly as typed on map CORPT0A (app/bms/CORPT00.bms),
 * carried as raw strings so the backend can apply CORPT00C's own edits rather
 * than a framework's.
 *
 * <p>The three report-type marks are the map's {@code MONTHLY}, {@code YEARLY}
 * and {@code CUSTOM} X(1) fields: the legacy test is "not spaces and not
 * low-values" (cbl:213, 239, 256), so any non-blank character selects.
 */
public class ReportSubmitRequest {

    /** MONTHLY — X(1) mark for the current-month report. */
    private String monthly;
    /** YEARLY — X(1) mark for the current-year report. */
    private String yearly;
    /** CUSTOM — X(1) mark for the typed date range. */
    private String custom;

    /** SDTMM — start date month, X(2). */
    private String startMonth;
    /** SDTDD — start date day, X(2). */
    private String startDay;
    /** SDTYYYY — start date year, X(4). */
    private String startYear;
    /** EDTMM — end date month, X(2). */
    private String endMonth;
    /** EDTDD — end date day, X(2). */
    private String endDay;
    /** EDTYYYY — end date year, X(4). */
    private String endYear;

    /** CONFIRM — X(1); blank asks, Y/y submits, N/n clears (cbl:464-494). */
    private String confirm;

    public String getMonthly() { return monthly; }
    public void setMonthly(String monthly) { this.monthly = monthly; }

    public String getYearly() { return yearly; }
    public void setYearly(String yearly) { this.yearly = yearly; }

    public String getCustom() { return custom; }
    public void setCustom(String custom) { this.custom = custom; }

    public String getStartMonth() { return startMonth; }
    public void setStartMonth(String startMonth) { this.startMonth = startMonth; }

    public String getStartDay() { return startDay; }
    public void setStartDay(String startDay) { this.startDay = startDay; }

    public String getStartYear() { return startYear; }
    public void setStartYear(String startYear) { this.startYear = startYear; }

    public String getEndMonth() { return endMonth; }
    public void setEndMonth(String endMonth) { this.endMonth = endMonth; }

    public String getEndDay() { return endDay; }
    public void setEndDay(String endDay) { this.endDay = endDay; }

    public String getEndYear() { return endYear; }
    public void setEndYear(String endYear) { this.endYear = endYear; }

    public String getConfirm() { return confirm; }
    public void setConfirm(String confirm) { this.confirm = confirm; }
}
