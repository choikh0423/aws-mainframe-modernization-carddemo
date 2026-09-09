package com.carddemo.reporting.dto;

/**
 * The six custom-range fields after CORPT00C's NUMVAL-C normalisation
 * (cbl:305-327), i.e. what the 3270 screen shows once the program has moved the
 * numeric work fields back into the map (FR-R18). The React screen writes these
 * back into its inputs so a rejected range looks the same as it did on 3270.
 */
public class CustomDateFields {

    private final String startMonth;
    private final String startDay;
    private final String startYear;
    private final String endMonth;
    private final String endDay;
    private final String endYear;

    public CustomDateFields(String startMonth, String startDay, String startYear,
                            String endMonth, String endDay, String endYear) {
        this.startMonth = startMonth;
        this.startDay = startDay;
        this.startYear = startYear;
        this.endMonth = endMonth;
        this.endDay = endDay;
        this.endYear = endYear;
    }

    public String getStartMonth() { return startMonth; }
    public String getStartDay() { return startDay; }
    public String getStartYear() { return startYear; }
    public String getEndMonth() { return endMonth; }
    public String getEndDay() { return endDay; }
    public String getEndYear() { return endYear; }

    /** {@code WS-START-DATE} — YYYY-MM-DD (cbl:60-65). */
    public String getStartDate() {
        return startYear + "-" + startMonth + "-" + startDay;
    }

    /** {@code WS-END-DATE} — YYYY-MM-DD (cbl:66-71). */
    public String getEndDate() {
        return endYear + "-" + endMonth + "-" + endDay;
    }
}
