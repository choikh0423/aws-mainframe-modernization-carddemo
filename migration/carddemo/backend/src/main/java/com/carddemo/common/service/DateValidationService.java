package com.carddemo.common.service;

import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;

/**
 * Java port of the COBOL date-validation utility {@code CSUTLDTC} (app/cbl/CSUTLDTC.cbl).
 *
 * <p>CSUTLDTC passes the candidate date and a format mask ({@code YYYY-MM-DD}, see
 * COTRN02C.cbl:60) to the LE service {@code CEEDAYS} and returns the feedback
 * severity in {@code CSUTLDTC-RESULT-SEV-CD}. A severity of {@code '0000'} means
 * the date is a real calendar date ("Date is valid"); any other severity means it
 * is not (bad value / invalid month / non-numeric / etc.), per the EVALUATE in
 * CSUTLDTC.cbl:130-152. COTRN02C treats {@code SEV-CD = '0000'} as the pass gate
 * (COTRN02C.cbl:397).
 *
 * <p>This service reproduces that contract for the {@code YYYY-MM-DD} mask used by
 * CT02: it checks the string is exactly {@code YYYY-MM-DD} and denotes a real
 * calendar date (rejecting e.g. {@code 2023-02-30}). {@link #isValid(String)}
 * mirrors the {@code SEV-CD = '0000'} success test.
 */
@Service
public class DateValidationService {

    /** Feedback mask CT02 hands to CSUTLDTC (COTRN02C.cbl:60). */
    public static final String YYYY_MM_DD = "yyyy-MM-dd";

    /** CSUTLDTC severity code returned for a valid date ("Date is valid"). */
    public static final String SEV_VALID = "0000";
    /** CSUTLDTC severity code for a failed validation (non-zero severity). */
    public static final String SEV_INVALID = "0012";

    private static final DateTimeFormatter STRICT_YMD = DateTimeFormatter
            .ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    /**
     * Validate a date string against the {@code YYYY-MM-DD} mask, mirroring a
     * single {@code CALL 'CSUTLDTC'}.
     *
     * @param date the candidate date (as typed on the CT02 screen)
     * @return a result carrying the CSUTLDTC-style severity code and message
     */
    public DateValidationResult validate(String date) {
        if (date == null) {
            return new DateValidationResult(false, SEV_INVALID, "Date is invalid");
        }
        String trimmed = date.trim();
        // Format-shape check mirrors COTRN02C.cbl:353-378 before the CSUTLDTC call.
        if (trimmed.length() != 10 || trimmed.charAt(4) != '-' || trimmed.charAt(7) != '-') {
            return new DateValidationResult(false, SEV_INVALID, "Date is invalid");
        }
        try {
            var parsed = STRICT_YMD.parse(trimmed);
            // Reject year 0 to mirror CEEDAYS FC-YEAR-IN-ERA-ZERO.
            if (parsed.get(ChronoField.YEAR_OF_ERA) == 0) {
                return new DateValidationResult(false, SEV_INVALID, "YearInEra is 0");
            }
        } catch (DateTimeParseException | java.time.temporal.UnsupportedTemporalTypeException ex) {
            return new DateValidationResult(false, SEV_INVALID, "Date is invalid");
        }
        return new DateValidationResult(true, SEV_VALID, "Date is valid");
    }

    /**
     * Convenience gate mirroring COTRN02C's {@code CSUTLDTC-RESULT-SEV-CD = '0000'}
     * test (COTRN02C.cbl:397).
     */
    public boolean isValid(String date) {
        return validate(date).isValid();
    }

    /** Immutable result of a CSUTLDTC-style date validation. */
    public static final class DateValidationResult {
        private final boolean valid;
        private final String severityCode;
        private final String message;

        public DateValidationResult(boolean valid, String severityCode, String message) {
            this.valid = valid;
            this.severityCode = severityCode;
            this.message = message;
        }

        public boolean isValid() { return valid; }
        public String getSeverityCode() { return severityCode; }
        public String getMessage() { return message; }
    }
}
