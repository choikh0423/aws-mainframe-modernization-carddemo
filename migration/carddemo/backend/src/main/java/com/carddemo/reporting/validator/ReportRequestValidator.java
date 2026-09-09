package com.carddemo.reporting.validator;

import com.carddemo.reporting.ReportingMessages;
import com.carddemo.reporting.ScreenField;
import com.carddemo.reporting.dto.CustomDateFields;
import com.carddemo.reporting.dto.ReportSubmitRequest;
import com.carddemo.reporting.exception.ReportValidationException;
import com.carddemo.reporting.service.CsutldtcFeedbackService;
import com.carddemo.reporting.service.ReportType;
import com.carddemo.reporting.util.CobolNumval;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * CORPT00C's PROCESS-ENTER-KEY edits (cbl:208-443), in the order the program runs
 * them. Every failure raises {@link ReportValidationException}, which is the
 * migrated form of {@code SEND-TRNRPT-SCREEN} + {@code GO TO RETURN-TO-CICS}: the
 * first failing check ends the interaction, so no later check runs (FR-R11).
 */
@Component
public class ReportRequestValidator {

    private final CsutldtcFeedbackService csutldtc;

    public ReportRequestValidator(CsutldtcFeedbackService csutldtc) {
        this.csutldtc = csutldtc;
    }

    /**
     * The report-type EVALUATE (cbl:212-443). A mark counts when it is neither
     * spaces nor low-values, and the WHEN order fixes the precedence
     * Monthly &gt; Yearly &gt; Custom (FR-R6).
     *
     * @throws ReportValidationException {@code Select a report type to print report...}
     *         when nothing is marked (FR-R7)
     */
    public ReportType resolveReportType(ReportSubmitRequest request) {
        if (marked(request.getMonthly())) {
            return ReportType.MONTHLY;
        }
        if (marked(request.getYearly())) {
            return ReportType.YEARLY;
        }
        if (marked(request.getCustom())) {
            return ReportType.CUSTOM;
        }
        throw new ReportValidationException(ReportingMessages.SELECT_REPORT_TYPE, ScreenField.MONTHLY);
    }

    /**
     * The custom-range edits (cbl:256-426): empty checks, NUMVAL-C normalisation,
     * month/day range checks and the two CSUTLDTC calls.
     *
     * @return the normalised fields, i.e. what the map shows after the program has
     *         moved the numeric work fields back (FR-R18)
     */
    public CustomDateFields validateCustomRange(ReportSubmitRequest request) {
        // cbl:258-303 — the six "can NOT be empty" checks, in map order.
        requireNotEmpty(request.getStartMonth(), ReportingMessages.START_MONTH_EMPTY, ScreenField.SDTMM);
        requireNotEmpty(request.getStartDay(), ReportingMessages.START_DAY_EMPTY, ScreenField.SDTDD);
        requireNotEmpty(request.getStartYear(), ReportingMessages.START_YEAR_EMPTY, ScreenField.SDTYYYY);
        requireNotEmpty(request.getEndMonth(), ReportingMessages.END_MONTH_EMPTY, ScreenField.EDTMM);
        requireNotEmpty(request.getEndDay(), ReportingMessages.END_DAY_EMPTY, ScreenField.EDTDD);
        requireNotEmpty(request.getEndYear(), ReportingMessages.END_YEAR_EMPTY, ScreenField.EDTYYYY);

        // cbl:305-327 — NUMVAL-C into PIC 99 / PIC 9999 and straight back into the map.
        CustomDateFields fields = new CustomDateFields(
                CobolNumval.numvalCInto(request.getStartMonth(), 2),
                CobolNumval.numvalCInto(request.getStartDay(), 2),
                CobolNumval.numvalCInto(request.getStartYear(), 4),
                CobolNumval.numvalCInto(request.getEndMonth(), 2),
                CobolNumval.numvalCInto(request.getEndDay(), 2),
                CobolNumval.numvalCInto(request.getEndYear(), 4));

        // cbl:329-379 — the range checks. The "IS NOT NUMERIC" half of each condition
        // can no longer fire (NUMVAL-C has just made every field numeric), which is why
        // the two "Not a valid Year..." checks are unreachable (FR-R25).
        requireAtMost(fields.getStartMonth(), "12", ReportingMessages.START_MONTH_INVALID,
                ScreenField.SDTMM, fields);
        requireAtMost(fields.getStartDay(), "31", ReportingMessages.START_DAY_INVALID,
                ScreenField.SDTDD, fields);
        requireAtMost(fields.getEndMonth(), "12", ReportingMessages.END_MONTH_INVALID,
                ScreenField.EDTMM, fields);
        requireAtMost(fields.getEndDay(), "31", ReportingMessages.END_DAY_INVALID,
                ScreenField.EDTDD, fields);

        // cbl:388-426 — CSUTLDTC on each assembled date; 2513 is tolerated (FR-R26).
        requireValidDate(fields.getStartDate(), ReportingMessages.START_DATE_INVALID,
                ScreenField.SDTMM, fields);
        requireValidDate(fields.getEndDate(), ReportingMessages.END_DATE_INVALID,
                ScreenField.EDTMM, fields);

        return fields;
    }

    /** {@code NOT = SPACES AND LOW-VALUES} (cbl:213). */
    private boolean marked(String value) {
        return Optional.ofNullable(value).filter(v -> !v.isBlank()).isPresent();
    }

    private void requireNotEmpty(String value, String message, String cursor) {
        if (value == null || value.isBlank()) {
            throw new ReportValidationException(message, cursor);
        }
    }

    /** The COBOL compares the X(2) field against a literal, so this stays a string compare. */
    private void requireAtMost(String value, String limit, String message, String cursor,
                               CustomDateFields fields) {
        if (value.compareTo(limit) > 0) {
            throw new ReportValidationException(message, cursor, fields);
        }
    }

    private void requireValidDate(String date, String message, String cursor,
                                  CustomDateFields fields) {
        if (!csutldtc.validate(date).isAcceptedByCorpt00c()) {
            throw new ReportValidationException(message, cursor, fields);
        }
    }
}
