package com.carddemo.reporting.validator;

import com.carddemo.common.service.DateValidationService;
import com.carddemo.reporting.ScreenField;
import com.carddemo.reporting.dto.CustomDateFields;
import com.carddemo.reporting.dto.ReportSubmitRequest;
import com.carddemo.reporting.exception.ReportValidationException;
import com.carddemo.reporting.service.CsutldtcFeedbackService;
import com.carddemo.reporting.service.ReportType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * FR-R6..FR-R28 — CORPT00C's PROCESS-ENTER-KEY edits (cbl:208-443), message text
 * and cursor position included.
 */
class ReportRequestValidatorTest {

    private final ReportRequestValidator validator =
            new ReportRequestValidator(new CsutldtcFeedbackService(new DateValidationService()));

    private static ReportSubmitRequest customRange(String sm, String sd, String sy,
                                                   String em, String ed, String ey) {
        ReportSubmitRequest r = new ReportSubmitRequest();
        r.setCustom("X");
        r.setStartMonth(sm);
        r.setStartDay(sd);
        r.setStartYear(sy);
        r.setEndMonth(em);
        r.setEndDay(ed);
        r.setEndYear(ey);
        return r;
    }

    private static ReportSubmitRequest validCustomRange() {
        return customRange("07", "01", "2023", "07", "31", "2023");
    }

    private ReportValidationException rejects(ReportSubmitRequest request) {
        return catchThrowableOfType(() -> validator.validateCustomRange(request),
                ReportValidationException.class);
    }

    @Nested
    @DisplayName("report type selection (cbl:212-256, 437-442)")
    class ReportTypeSelection {

        @Test
        @DisplayName("FR-R6: any non-blank character marks the report type")
        void anyCharacterSelects() {
            ReportSubmitRequest request = new ReportSubmitRequest();
            request.setYearly("x");

            assertThat(validator.resolveReportType(request)).isEqualTo(ReportType.YEARLY);
        }

        @Test
        @DisplayName("FR-R6: Monthly wins over Yearly, Yearly over Custom")
        void evaluateOrderFixesPrecedence() {
            ReportSubmitRequest all = new ReportSubmitRequest();
            all.setMonthly("S");
            all.setYearly("S");
            all.setCustom("S");
            assertThat(validator.resolveReportType(all)).isEqualTo(ReportType.MONTHLY);

            ReportSubmitRequest yearlyAndCustom = new ReportSubmitRequest();
            yearlyAndCustom.setYearly("S");
            yearlyAndCustom.setCustom("S");
            assertThat(validator.resolveReportType(yearlyAndCustom)).isEqualTo(ReportType.YEARLY);
        }

        @Test
        @DisplayName("FR-R7: nothing marked is rejected with the cursor on MONTHLY")
        void noSelectionIsRejected() {
            ReportSubmitRequest blank = new ReportSubmitRequest();
            blank.setMonthly(" ");

            ReportValidationException ex = catchThrowableOfType(
                    () -> validator.resolveReportType(blank), ReportValidationException.class);

            assertThat(ex).hasMessage("Select a report type to print report...");
            assertThat(ex.getCursor()).isEqualTo(ScreenField.MONTHLY);
        }
    }

    @Nested
    @DisplayName("empty custom date components (cbl:258-303)")
    class EmptyComponents {

        @Test
        @DisplayName("FR-R12")
        void startMonth() {
            ReportValidationException ex = rejects(
                    customRange("  ", "01", "2023", "07", "31", "2023"));

            assertThat(ex).hasMessage("Start Date - Month can NOT be empty...");
            assertThat(ex.getCursor()).isEqualTo(ScreenField.SDTMM);
        }

        @Test
        @DisplayName("FR-R13")
        void startDay() {
            ReportValidationException ex = rejects(
                    customRange("07", null, "2023", "07", "31", "2023"));

            assertThat(ex).hasMessage("Start Date - Day can NOT be empty...");
            assertThat(ex.getCursor()).isEqualTo(ScreenField.SDTDD);
        }

        @Test
        @DisplayName("FR-R14")
        void startYear() {
            ReportValidationException ex = rejects(
                    customRange("07", "01", "", "07", "31", "2023"));

            assertThat(ex).hasMessage("Start Date - Year can NOT be empty...");
            assertThat(ex.getCursor()).isEqualTo(ScreenField.SDTYYYY);
        }

        @Test
        @DisplayName("FR-R15")
        void endMonth() {
            ReportValidationException ex = rejects(
                    customRange("07", "01", "2023", " ", "31", "2023"));

            assertThat(ex).hasMessage("End Date - Month can NOT be empty...");
            assertThat(ex.getCursor()).isEqualTo(ScreenField.EDTMM);
        }

        @Test
        @DisplayName("FR-R16")
        void endDay() {
            ReportValidationException ex = rejects(
                    customRange("07", "01", "2023", "07", " ", "2023"));

            assertThat(ex).hasMessage("End Date - Day can NOT be empty...");
            assertThat(ex.getCursor()).isEqualTo(ScreenField.EDTDD);
        }

        @Test
        @DisplayName("FR-R17")
        void endYear() {
            ReportValidationException ex = rejects(
                    customRange("07", "01", "2023", "07", "31", " "));

            assertThat(ex).hasMessage("End Date - Year can NOT be empty...");
            assertThat(ex.getCursor()).isEqualTo(ScreenField.EDTYYYY);
        }

        @Test
        @DisplayName("FR-R11: the first empty field wins — a later one is never reported")
        void firstFailureWins() {
            ReportValidationException ex = rejects(customRange(" ", " ", " ", " ", " ", " "));

            assertThat(ex).hasMessage("Start Date - Month can NOT be empty...");
        }
    }

    @Nested
    @DisplayName("normalisation and range checks (cbl:305-379)")
    class RangeChecks {

        @Test
        @DisplayName("FR-R18: accepted input comes back zero-padded")
        void normalisesAcceptedInput() {
            CustomDateFields fields = validator.validateCustomRange(
                    customRange("7", "1", "2023", "7", "31", "2023"));

            assertThat(fields.getStartMonth()).isEqualTo("07");
            assertThat(fields.getStartDay()).isEqualTo("01");
            assertThat(fields.getStartDate()).isEqualTo("2023-07-01");
            assertThat(fields.getEndDate()).isEqualTo("2023-07-31");
        }

        @Test
        @DisplayName("FR-R19")
        void startMonthAbove12() {
            ReportValidationException ex = rejects(
                    customRange("13", "01", "2023", "07", "31", "2023"));

            assertThat(ex).hasMessage("Start Date - Not a valid Month...");
            assertThat(ex.getCursor()).isEqualTo(ScreenField.SDTMM);
            assertThat(ex.getDateFields().getStartMonth()).isEqualTo("13");
        }

        @Test
        @DisplayName("FR-R20")
        void startDayAbove31() {
            ReportValidationException ex = rejects(
                    customRange("07", "32", "2023", "07", "31", "2023"));

            assertThat(ex).hasMessage("Start Date - Not a valid Day...");
            assertThat(ex.getCursor()).isEqualTo(ScreenField.SDTDD);
        }

        @Test
        @DisplayName("FR-R21")
        void endMonthAbove12() {
            ReportValidationException ex = rejects(
                    customRange("07", "01", "2023", "13", "31", "2023"));

            assertThat(ex).hasMessage("End Date - Not a valid Month...");
            assertThat(ex.getCursor()).isEqualTo(ScreenField.EDTMM);
        }

        @Test
        @DisplayName("FR-R22")
        void endDayAbove31() {
            ReportValidationException ex = rejects(
                    customRange("07", "01", "2023", "07", "32", "2023"));

            assertThat(ex).hasMessage("End Date - Not a valid Day...");
            assertThat(ex.getCursor()).isEqualTo(ScreenField.EDTDD);
        }

        @Test
        @DisplayName("FR-R11: the start-date checks run before the end-date ones")
        void startBeforeEnd() {
            ReportValidationException ex = rejects(
                    customRange("13", "01", "2023", "13", "31", "2023"));

            assertThat(ex).hasMessage("Start Date - Not a valid Month...");
        }
    }

    @Nested
    @DisplayName("CSUTLDTC checks (cbl:388-426)")
    class DateChecks {

        @Test
        @DisplayName("FR-R23")
        void startDateRejected() {
            ReportValidationException ex = rejects(
                    customRange("02", "30", "2023", "07", "31", "2023"));

            assertThat(ex).hasMessage("Start Date - Not a valid date...");
            assertThat(ex.getCursor()).isEqualTo(ScreenField.SDTMM);
        }

        @Test
        @DisplayName("FR-R24")
        void endDateRejected() {
            ReportValidationException ex = rejects(
                    customRange("07", "01", "2023", "04", "31", "2023"));

            assertThat(ex).hasMessage("End Date - Not a valid date...");
            assertThat(ex.getCursor()).isEqualTo(ScreenField.EDTMM);
        }

        @Test
        @DisplayName("FR-R28: 02/31 passes the '> 31' test and is caught here instead")
        void impossibleDayOfMonthReachesCsutldtc() {
            assertThatThrownBy(() -> validator.validateCustomRange(
                    customRange("02", "31", "2023", "07", "31", "2023")))
                    .hasMessage("Start Date - Not a valid date...");
        }

        @Test
        @DisplayName("FR-R25: typed letters become 00/00/0000 and fail as an invalid date, "
                + "not as an invalid month")
        void lettersFailAsInvalidDate() {
            ReportValidationException ex = rejects(
                    customRange("ab", "cd", "efgh", "07", "31", "2023"));

            assertThat(ex).hasMessage("Start Date - Not a valid date...");
            assertThat(ex.getDateFields().getStartDate()).isEqualTo("0000-00-00");
        }

        @Test
        @DisplayName("FR-R26: a date before the Lillian epoch is accepted")
        void unsupportedRangeIsAccepted() {
            CustomDateFields fields = validator.validateCustomRange(
                    customRange("01", "01", "1500", "12", "31", "1500"));

            assertThat(fields.getStartDate()).isEqualTo("1500-01-01");
        }

        @Test
        @DisplayName("a well-formed range passes every edit")
        void validRangePasses() {
            assertThat(validator.validateCustomRange(validCustomRange()).getStartDate())
                    .isEqualTo("2023-07-01");
        }
    }
}
