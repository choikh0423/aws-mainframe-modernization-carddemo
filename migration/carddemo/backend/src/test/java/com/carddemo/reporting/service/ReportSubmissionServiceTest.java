package com.carddemo.reporting.service;

import com.carddemo.common.service.DateValidationService;
import com.carddemo.reporting.ScreenField;
import com.carddemo.reporting.dto.ReportSubmitRequest;
import com.carddemo.reporting.dto.ReportSubmitResponse;
import com.carddemo.reporting.exception.ReportJobSubmissionException;
import com.carddemo.reporting.exception.ReportValidationException;
import com.carddemo.reporting.port.TransactionReportJobLauncher;
import com.carddemo.reporting.port.TransactionReportJobRequest;
import com.carddemo.reporting.validator.ReportRequestValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * FR-R6..FR-R38 at the service level — CORPT00C's PROCESS-ENTER-KEY and
 * SUBMIT-JOB-TO-INTRDR (cbl:208-510): the confirmation gate, the exact message
 * text, and what actually reaches the S-07 → S-14 port.
 */
class ReportSubmissionServiceTest {

    /** Stands in for the internal reader: records what was submitted, or fails on demand. */
    private static final class FakeLauncher implements TransactionReportJobLauncher {

        private final List<TransactionReportJobRequest> submitted = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public void launch(TransactionReportJobRequest request) {
            if (failure != null) {
                throw failure;
            }
            submitted.add(request);
        }
    }

    private final FakeLauncher launcher = new FakeLauncher();

    private final ReportSubmissionService service = new ReportSubmissionService(
            new ReportRequestValidator(new CsutldtcFeedbackService(new DateValidationService())),
            new ReportPeriodResolver(Clock.fixed(Instant.parse("2023-07-15T12:00:00Z"), ZoneOffset.UTC)),
            launcher);

    private static ReportSubmitRequest monthly(String confirm) {
        ReportSubmitRequest r = new ReportSubmitRequest();
        r.setMonthly("S");
        r.setConfirm(confirm);
        return r;
    }

    private static ReportSubmitRequest custom(String confirm) {
        ReportSubmitRequest r = new ReportSubmitRequest();
        r.setCustom("S");
        r.setStartMonth("07");
        r.setStartDay("01");
        r.setStartYear("2023");
        r.setEndMonth("07");
        r.setEndDay("31");
        r.setEndYear("2023");
        r.setConfirm(confirm);
        return r;
    }

    private ReportValidationException rejects(ReportSubmitRequest request) {
        return catchThrowableOfType(() -> service.submit(request), ReportValidationException.class);
    }

    @Test
    @DisplayName("FR-R29: a blank confirmation asks, naming the report, and submits nothing")
    void blankConfirmAsksForMonthly() {
        ReportValidationException ex = rejects(monthly(""));

        assertThat(ex).hasMessage("Please confirm to print the Monthly report...");
        assertThat(ex.getCursor()).isEqualTo(ScreenField.CONFIRM);
        assertThat(launcher.submitted).isEmpty();
    }

    @Test
    @DisplayName("FR-R29: the Yearly report is named in its confirmation too")
    void blankConfirmAsksForYearly() {
        ReportSubmitRequest request = new ReportSubmitRequest();
        request.setYearly("S");

        assertThat(rejects(request)).hasMessage("Please confirm to print the Yearly report...");
    }

    @Test
    @DisplayName("FR-R29: so is the Custom one, once its dates have passed every edit")
    void blankConfirmAsksForCustom() {
        ReportValidationException ex = rejects(custom(null));

        assertThat(ex).hasMessage("Please confirm to print the Custom report...");
        assertThat(ex.getDateFields().getStartDate()).isEqualTo("2023-07-01");
    }

    @Test
    @DisplayName("FR-R11: the date edits run before the confirmation is looked at")
    void dateEditsPrecedeTheConfirmation() {
        ReportSubmitRequest request = custom("");
        request.setStartMonth("13");

        assertThat(rejects(request)).hasMessage("Start Date - Not a valid Month...");
    }

    @Test
    @DisplayName("FR-R30 / FR-R36: Y submits and shows the green success line")
    void confirmYSubmitsMonthly() {
        ReportSubmitResponse response = service.submit(monthly("Y"));

        assertThat(response.isSubmitted()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Monthly report submitted for printing ...");
        assertThat(response.getCursor()).isEqualTo(ScreenField.MONTHLY);
        assertThat(response.isFieldsCleared()).isTrue();
    }

    @Test
    @DisplayName("FR-R30: lower-case y submits as well")
    void confirmLowerCaseYSubmits() {
        assertThat(service.submit(monthly("y")).isSubmitted()).isTrue();
    }

    @Test
    @DisplayName("FR-R37: exactly one launch carries the report name and the resolved range")
    void launchCarriesTheRange() {
        service.submit(monthly("Y"));

        assertThat(launcher.submitted).singleElement().satisfies(request -> {
            assertThat(request.getReportName()).isEqualTo("Monthly");
            assertThat(request.getStartDateText()).isEqualTo("2023-07-01");
            assertThat(request.getEndDateText()).isEqualTo("2023-07-31");
        });
    }

    @Test
    @DisplayName("FR-R10 / FR-R37: a custom range reaches the job exactly as typed")
    void customRangeReachesTheJob() {
        ReportSubmitResponse response = service.submit(custom("Y"));

        assertThat(response.getMessage()).isEqualTo("Custom report submitted for printing ...");
        assertThat(launcher.submitted).singleElement().satisfies(request -> {
            assertThat(request.getReportName()).isEqualTo("Custom");
            assertThat(request.getStartDateText()).isEqualTo("2023-07-01");
            assertThat(request.getEndDateText()).isEqualTo("2023-07-31");
        });
    }

    @Test
    @DisplayName("FR-R27: an end date before the start date is submitted unchanged")
    void backwardsRangeIsSubmitted() {
        ReportSubmitRequest request = custom("Y");
        request.setStartMonth("12");
        request.setStartDay("31");
        request.setEndMonth("01");
        request.setEndDay("01");

        service.submit(request);

        assertThat(launcher.submitted).singleElement().satisfies(job -> {
            assertThat(job.getStartDateText()).isEqualTo("2023-12-31");
            assertThat(job.getEndDateText()).isEqualTo("2023-01-01");
        });
    }

    @Test
    @DisplayName("FR-R31: N clears the screen, shows no message and submits nothing")
    void confirmNClearsTheScreen() {
        ReportSubmitResponse response = service.submit(monthly("N"));

        assertThat(response.isSubmitted()).isFalse();
        assertThat(response.getMessage()).isEmpty();
        assertThat(response.isFieldsCleared()).isTrue();
        assertThat(launcher.submitted).isEmpty();
    }

    @Test
    @DisplayName("FR-R31: lower-case n does the same")
    void confirmLowerCaseNClearsTheScreen() {
        assertThat(service.submit(monthly("n")).isSubmitted()).isFalse();
    }

    @Test
    @DisplayName("FR-R32: any other character is quoted back verbatim")
    void invalidConfirmValue() {
        ReportValidationException ex = rejects(monthly("X"));

        assertThat(ex).hasMessage("\"X\" is not a valid value to confirm...");
        assertThat(ex.getCursor()).isEqualTo(ScreenField.CONFIRM);
        assertThat(launcher.submitted).isEmpty();
    }

    @Test
    @DisplayName("FR-R35: a failed submission shows the TDQ error, cursor on MONTHLY")
    void failedSubmissionShowsTheTdqError() {
        launcher.failure = new IllegalStateException("job repository down");

        ReportJobSubmissionException ex = catchThrowableOfType(
                () -> service.submit(monthly("Y")), ReportJobSubmissionException.class);

        assertThat(ex).hasMessage("Unable to Write TDQ (JOBS)...");
        assertThat(ex.getCursor()).isEqualTo(ScreenField.MONTHLY);
        assertThat(ex).hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("FR-R7: with no report type marked nothing is resolved and nothing launched")
    void noReportTypeSelected() {
        ReportValidationException ex = rejects(new ReportSubmitRequest());

        assertThat(ex).hasMessage("Select a report type to print report...");
        assertThat(launcher.submitted).isEmpty();
    }
}
