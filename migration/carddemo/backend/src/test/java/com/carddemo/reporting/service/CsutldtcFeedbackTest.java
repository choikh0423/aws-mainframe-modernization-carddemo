package com.carddemo.reporting.service;

import com.carddemo.common.service.DateValidationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-R23 / FR-R24 / FR-R26 — the {@code CALL 'CSUTLDTC'} gate CORPT00C applies to
 * each assembled date (cbl:396-406, 416-426), including the {@code 2513} escape.
 */
class CsutldtcFeedbackTest {

    private final CsutldtcFeedbackService csutldtc =
            new CsutldtcFeedbackService(new DateValidationService());

    @Test
    @DisplayName("a real date passes with severity 0000")
    void realDatePasses() {
        CsutldtcFeedbackService.Feedback feedback = csutldtc.validate("2023-07-15");

        assertThat(feedback.getSeverityCode()).isEqualTo("0000");
        assertThat(feedback.isAcceptedByCorpt00c()).isTrue();
    }

    @Test
    @DisplayName("a day that does not exist in its month is rejected")
    void impossibleDayFails() {
        CsutldtcFeedbackService.Feedback feedback = csutldtc.validate("2023-02-31");

        assertThat(feedback.getSeverityCode()).isEqualTo("0012");
        assertThat(feedback.getMessageNumber()).isNotEqualTo("2513");
        assertThat(feedback.isAcceptedByCorpt00c()).isFalse();
    }

    @Test
    @DisplayName("the all-zero date NUMVAL-C produces from garbage is rejected")
    void zeroDateFails() {
        assertThat(csutldtc.validate("0000-00-00").isAcceptedByCorpt00c()).isFalse();
    }

    @Test
    @DisplayName("year zero is FC-YEAR-IN-ERA-ZERO (2521), not 2513, so it is rejected")
    void yearZeroFails() {
        CsutldtcFeedbackService.Feedback feedback = csutldtc.validate("0000-01-01");

        assertThat(feedback.getMessageNumber())
                .isEqualTo(CsutldtcFeedbackService.MSG_YEAR_IN_ERA_ZERO);
        assertThat(feedback.isAcceptedByCorpt00c()).isFalse();
    }

    @Test
    @DisplayName("FR-R26: a real date before the Lillian epoch returns 2513 and is accepted")
    void unsupportedRangeIsAccepted() {
        CsutldtcFeedbackService.Feedback feedback = csutldtc.validate("1500-01-01");

        assertThat(feedback.getSeverityCode()).isEqualTo("0012");
        assertThat(feedback.getMessageNumber())
                .isEqualTo(CsutldtcFeedbackService.MSG_UNSUPPORTED_RANGE);
        assertThat(feedback.isAcceptedByCorpt00c()).isTrue();
    }

    @Test
    @DisplayName("1582-10-15 itself is inside the supported range")
    void lillianEpochIsSupported() {
        CsutldtcFeedbackService.Feedback feedback = csutldtc.validate("1582-10-15");

        assertThat(feedback.getSeverityCode()).isEqualTo("0000");
    }
}
