package com.carddemo.reporting.service;

import com.carddemo.reporting.ReportingMessages;
import com.carddemo.reporting.ScreenField;
import com.carddemo.reporting.dto.CustomDateFields;
import com.carddemo.reporting.dto.ReportSubmitRequest;
import com.carddemo.reporting.dto.ReportSubmitResponse;
import com.carddemo.reporting.exception.ReportJobSubmissionException;
import com.carddemo.reporting.exception.ReportValidationException;
import com.carddemo.reporting.port.TransactionReportJobLauncher;
import com.carddemo.reporting.port.TransactionReportJobRequest;
import com.carddemo.reporting.validator.ReportRequestValidator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * CR00 ENTER — CORPT00C's PROCESS-ENTER-KEY and SUBMIT-JOB-TO-INTRDR
 * (cbl:208-510) end to end: pick the report type, resolve or validate its date
 * range, apply the confirmation gate, and launch the TRANREPT job through the
 * S-07 → S-14 port.
 *
 * <p>The paragraph order matters and is preserved: for a custom range every date
 * edit runs <em>before</em> the confirmation is looked at (cbl:434-435), while
 * Monthly and Yearly go straight to it (cbl:238, 255).
 */
@Service
public class ReportSubmissionService {

    private final ReportRequestValidator validator;
    private final ReportPeriodResolver periodResolver;
    private final TransactionReportJobLauncher jobLauncher;

    public ReportSubmissionService(ReportRequestValidator validator,
                                   ReportPeriodResolver periodResolver,
                                   TransactionReportJobLauncher jobLauncher) {
        this.validator = validator;
        this.periodResolver = periodResolver;
        this.jobLauncher = jobLauncher;
    }

    public ReportSubmitResponse submit(ReportSubmitRequest request) {
        ReportType type = validator.resolveReportType(request);

        ReportPeriod period;
        CustomDateFields dateFields = null;
        switch (type) {
            case MONTHLY -> period = periodResolver.monthly();
            case YEARLY -> period = periodResolver.yearly();
            default -> {
                dateFields = validator.validateCustomRange(request);
                period = new ReportPeriod(ReportType.CUSTOM,
                        LocalDate.parse(dateFields.getStartDate()),
                        LocalDate.parse(dateFields.getEndDate()));
            }
        }

        return confirmAndSubmit(request.getConfirm(), period, dateFields);
    }

    /** SUBMIT-JOB-TO-INTRDR (cbl:462-510). */
    private ReportSubmitResponse confirmAndSubmit(String rawConfirm, ReportPeriod period,
                                                  CustomDateFields dateFields) {
        // CONFIRM is PIC X(1) on the map, so only its first character can ever reach the program.
        String confirm = rawConfirm == null || rawConfirm.isEmpty()
                ? ""
                : rawConfirm.substring(0, 1);

        if (confirm.isBlank()) {
            throw new ReportValidationException(
                    ReportingMessages.confirmToPrint(period.getReportName()),
                    ScreenField.CONFIRM, dateFields);
        }
        if ("N".equals(confirm) || "n".equals(confirm)) {
            // INITIALIZE-ALL-FIELDS then a plain send: the screen comes back empty with
            // no message at all, because WS-MESSAGE was cleared too (cbl:480-483, 646).
            return new ReportSubmitResponse(false, period.getReportName(),
                    period.getStartDate().toString(), period.getEndDate().toString(),
                    "", ScreenField.MONTHLY);
        }
        if (!"Y".equals(confirm) && !"y".equals(confirm)) {
            throw new ReportValidationException(
                    ReportingMessages.invalidConfirmValue(confirm), ScreenField.CONFIRM, dateFields);
        }

        launch(period);

        return new ReportSubmitResponse(true, period.getReportName(),
                period.getStartDate().toString(), period.getEndDate().toString(),
                ReportingMessages.reportSubmitted(period.getReportName()), ScreenField.MONTHLY);
    }

    /**
     * The write to the internal reader (cbl:498-523). A failure of the launch is
     * the target's non-NORMAL RESP from {@code WRITEQ TD} (FR-R35).
     */
    private void launch(ReportPeriod period) {
        TransactionReportJobRequest jobRequest = new TransactionReportJobRequest(
                period.getReportName(), period.getStartDate(), period.getEndDate());
        try {
            jobLauncher.launch(jobRequest);
        } catch (RuntimeException ex) {
            throw new ReportJobSubmissionException(ReportingMessages.UNABLE_TO_WRITE_TDQ,
                    ScreenField.MONTHLY, ex);
        }
    }
}
