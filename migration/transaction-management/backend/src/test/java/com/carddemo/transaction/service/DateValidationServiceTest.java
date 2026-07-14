package com.carddemo.transaction.service;

import com.carddemo.transaction.service.DateValidationService.DateValidationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for the CSUTLDTC port. Valid real calendar dates in the
 * YYYY-MM-DD mask yield severity "0000"; anything else is rejected.
 */
class DateValidationServiceTest {

    private final DateValidationService service = new DateValidationService();

    @Test
    void acceptsRealDate() {
        DateValidationResult r = service.validate("2023-06-15");
        assertThat(r.isValid()).isTrue();
        assertThat(r.getSeverityCode()).isEqualTo(DateValidationService.SEV_VALID);
    }

    @Test
    void acceptsLeapDay() {
        assertThat(service.isValid("2024-02-29")).isTrue();
    }

    @Test
    void rejectsNonLeapFeb29() {
        assertThat(service.isValid("2023-02-29")).isFalse();
    }

    @Test
    void rejectsImpossibleDay() {
        assertThat(service.isValid("2023-02-30")).isFalse();
    }

    @Test
    void rejectsInvalidMonth() {
        assertThat(service.isValid("2023-13-01")).isFalse();
    }

    @Test
    void rejectsWrongFormat() {
        assertThat(service.isValid("06/15/2023")).isFalse();
        assertThat(service.isValid("2023-6-15")).isFalse();
        assertThat(service.isValid("20230615")).isFalse();
    }

    @Test
    void rejectsNonNumeric() {
        assertThat(service.isValid("YYYY-MM-DD")).isFalse();
        assertThat(service.isValid("2023-AB-15")).isFalse();
    }

    @Test
    void rejectsNullOrBlank() {
        assertThat(service.isValid(null)).isFalse();
        assertThat(service.isValid("")).isFalse();
        assertThat(service.isValid("          ")).isFalse();
    }

    @Test
    void invalidDateCarriesNonZeroSeverity() {
        DateValidationResult r = service.validate("2023-02-30");
        assertThat(r.isValid()).isFalse();
        assertThat(r.getSeverityCode()).isNotEqualTo(DateValidationService.SEV_VALID);
    }
}
