package com.carddemo.reporting;

/**
 * The CR00 screen literals, reproduced verbatim from CORPT00C.cbl. The trailing
 * {@code ...} belongs to the COBOL literal and must not be trimmed: the operator
 * sees exactly this text in ERRMSG on map CORPT0A.
 */
public final class ReportingMessages {

    private ReportingMessages() {
    }

    /** CORPT00C.cbl:438 - ENTER with no report type marked. */
    public static final String SELECT_REPORT_TYPE = "Select a report type to print report...";

    /** CORPT00C.cbl:261. */
    public static final String START_MONTH_EMPTY = "Start Date - Month can NOT be empty...";
    /** CORPT00C.cbl:268. */
    public static final String START_DAY_EMPTY = "Start Date - Day can NOT be empty...";
    /** CORPT00C.cbl:275. */
    public static final String START_YEAR_EMPTY = "Start Date - Year can NOT be empty...";
    /** CORPT00C.cbl:282. */
    public static final String END_MONTH_EMPTY = "End Date - Month can NOT be empty...";
    /** CORPT00C.cbl:289. */
    public static final String END_DAY_EMPTY = "End Date - Day can NOT be empty...";
    /** CORPT00C.cbl:296. */
    public static final String END_YEAR_EMPTY = "End Date - Year can NOT be empty...";

    /** CORPT00C.cbl:331. */
    public static final String START_MONTH_INVALID = "Start Date - Not a valid Month...";
    /** CORPT00C.cbl:340. */
    public static final String START_DAY_INVALID = "Start Date - Not a valid Day...";
    /**
     * CORPT00C.cbl:348. Unreachable in the legacy program and in the migration:
     * the year has already been forced numeric by NUMVAL-C (FR-R25). Kept so the
     * literal exists where the source has it.
     */
    public static final String START_YEAR_INVALID = "Start Date - Not a valid Year...";
    /** CORPT00C.cbl:357. */
    public static final String END_MONTH_INVALID = "End Date - Not a valid Month...";
    /** CORPT00C.cbl:366. */
    public static final String END_DAY_INVALID = "End Date - Not a valid Day...";
    /** CORPT00C.cbl:374. Unreachable for the same reason as {@link #START_YEAR_INVALID}. */
    public static final String END_YEAR_INVALID = "End Date - Not a valid Year...";

    /** CORPT00C.cbl:400 - CSUTLDTC rejected the assembled start date. */
    public static final String START_DATE_INVALID = "Start Date - Not a valid date...";
    /** CORPT00C.cbl:420 - CSUTLDTC rejected the assembled end date. */
    public static final String END_DATE_INVALID = "End Date - Not a valid date...";

    /** CORPT00C.cbl:531 - the WRITEQ TD to queue JOBS failed. */
    public static final String UNABLE_TO_WRITE_TDQ = "Unable to Write TDQ (JOBS)...";

    /**
     * CORPT00C.cbl:465-470 - blank CONFIRM. The COBOL STRING delimits the report
     * name by a space, so only the name itself reaches the message.
     */
    public static String confirmToPrint(String reportName) {
        return "Please confirm to print the " + delimitedBySpace(reportName) + " report...";
    }

    /**
     * CORPT00C.cbl:485-490 - CONFIRM held something other than Y/y/N/n. CONFIRM is
     * PIC X(1), so the quoted text is the single typed character.
     */
    public static String invalidConfirmValue(String confirm) {
        return "\"" + delimitedBySpace(confirm) + "\" is not a valid value to confirm...";
    }

    /** CORPT00C.cbl:449-452 - the green success line after a submitted job. */
    public static String reportSubmitted(String reportName) {
        return delimitedBySpace(reportName) + " report submitted for printing ...";
    }

    /** COBOL {@code STRING ... DELIMITED BY SPACE}: everything before the first space. */
    private static String delimitedBySpace(String value) {
        if (value == null) {
            return "";
        }
        int end = value.indexOf(' ');
        return end < 0 ? value : value.substring(0, end);
    }
}
