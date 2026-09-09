package com.carddemo.reporting.service;

import com.carddemo.common.service.DateValidationService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * CORPT00C's two {@code CALL 'CSUTLDTC'} sites (cbl:392-406, 412-426) need more
 * of the feedback area than the shared service exposes: they accept a date whose
 * severity is non-zero as long as the message number is {@code 2513}
 * (cbl:399, 419). This wraps
 * {@link DateValidationService} — the shared CSUTLDTC seam, which stays
 * untouched — and adds the message number that decision needs.
 *
 * <p>{@code 2513} is {@code FC-UNSUPP-RANGE} (CSUTLDTC.cbl:66, token
 * {@code X'000309D1'} = 2513): a syntactically real date that falls outside the
 * range CEEDAYS supports, i.e. before the Lillian epoch {@code 1582-10-15}. Such
 * a date reaches the submitted job (FR-R26).
 *
 * <p>The other cause codes are not reproduced individually because CORPT00C only
 * ever tests "is it 2513": every other failure is reported with severity
 * {@code 0012} and a non-2513 number, which is exactly the behaviour the screen
 * depends on.
 */
@Service
public class CsutldtcFeedbackService {

    /** {@code FC-UNSUPP-RANGE} — the one non-zero severity CORPT00C tolerates. */
    public static final String MSG_UNSUPPORTED_RANGE = "2513";
    /** {@code FC-YEAR-IN-ERA-ZERO} (CSUTLDTC.cbl:70, token X'000309D9'). */
    public static final String MSG_YEAR_IN_ERA_ZERO = "2521";
    /** {@code FC-BAD-DATE-VALUE} (CSUTLDTC.cbl:64, token X'000309CC'). */
    public static final String MSG_BAD_DATE_VALUE = "2508";
    /** No condition raised: CEEDAYS returned a Lillian day number. */
    public static final String MSG_NONE = "0000";

    /** First date CEEDAYS can convert; earlier dates come back as 2513. */
    private static final LocalDate LILLIAN_EPOCH = LocalDate.of(1582, 10, 15);

    private final DateValidationService dateValidationService;

    public CsutldtcFeedbackService(DateValidationService dateValidationService) {
        this.dateValidationService = dateValidationService;
    }

    /** One {@code CALL 'CSUTLDTC'} with the {@code YYYY-MM-DD} mask (cbl:388-394). */
    public Feedback validate(String date) {
        DateValidationService.DateValidationResult result = dateValidationService.validate(date);
        if (yearIsZero(date)) {
            return new Feedback(DateValidationService.SEV_INVALID, MSG_YEAR_IN_ERA_ZERO,
                    "YearInEra is 0");
        }
        if (result.isValid()) {
            if (beforeLillianEpoch(date)) {
                return new Feedback(DateValidationService.SEV_INVALID, MSG_UNSUPPORTED_RANGE,
                        "Unsupp. Range");
            }
            return new Feedback(DateValidationService.SEV_VALID, MSG_NONE, result.getMessage());
        }
        return new Feedback(DateValidationService.SEV_INVALID, MSG_BAD_DATE_VALUE,
                result.getMessage());
    }

    /**
     * CEEDAYS raises FC-YEAR-IN-ERA-ZERO for year 0000 and CORPT00C rejects it,
     * so it is tested before the unsupported-range escape: java.time reads 0000
     * as 1 BCE, which would otherwise look like a tolerable pre-Lillian date.
     */
    private boolean yearIsZero(String date) {
        return date != null && date.trim().startsWith("0000-");
    }

    private boolean beforeLillianEpoch(String date) {
        try {
            return LocalDate.parse(date.trim()).isBefore(LILLIAN_EPOCH);
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    /** {@code CSUTLDTC-RESULT}: the severity, the message number and the text. */
    public static final class Feedback {

        private final String severityCode;
        private final String messageNumber;
        private final String message;

        public Feedback(String severityCode, String messageNumber, String message) {
            this.severityCode = severityCode;
            this.messageNumber = messageNumber;
            this.message = message;
        }

        public String getSeverityCode() { return severityCode; }
        public String getMessageNumber() { return messageNumber; }
        public String getMessage() { return message; }

        /**
         * CORPT00C's gate: severity {@code 0000}, or any severity whose message
         * number is {@code 2513} (cbl:396-406).
         */
        public boolean isAcceptedByCorpt00c() {
            return DateValidationService.SEV_VALID.equals(severityCode)
                    || MSG_UNSUPPORTED_RANGE.equals(messageNumber);
        }
    }
}
