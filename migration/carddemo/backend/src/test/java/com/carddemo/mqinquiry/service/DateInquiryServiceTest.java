package com.carddemo.mqinquiry.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** CDRD business rules: FR-MQI-020..022 / FR-CODATE01-01..05. */
class DateInquiryServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2024-03-09T21:07:05Z"), ZoneId.of("UTC"));

    @Test
    void repliesWithTheSystemDateAndTimeInTheLegacyEditedFormats() {
        String reply = new DateInquiryService(FIXED).inquire().payload();

        assertThat(reply).hasSize(1000);
        assertThat(reply.substring(0, 14)).isEqualTo("SYSTEM DATE : ");
        assertThat(reply.substring(14, 24)).isEqualTo("03-09-2024");
        assertThat(reply.substring(24, 38)).isEqualTo("SYSTEM TIME : ");
        assertThat(reply.substring(38, 46)).isEqualTo("21:07:05");
        assertThat(reply.substring(46)).isBlank();
    }

    @Test
    void answersAnyRequestWhateverItsFunctionAndKey() {
        DateInquiryService service = new DateInquiryService(FIXED);

        assertThat(service.inquire().payload()).isEqualTo(service.inquire().payload());
        assertThat(service.inquire().errorReport()).isFalse();
    }

    @Test
    void takesTheDateAndTimeFromOneClockReading() {
        Clock lastInstantOfTheDay = Clock.fixed(Instant.parse("2024-12-31T23:59:59Z"), ZoneId.of("UTC"));

        String reply = new DateInquiryService(lastInstantOfTheDay).inquire().payload();

        assertThat(reply.substring(14, 24)).isEqualTo("12-31-2024");
        assertThat(reply.substring(38, 46)).isEqualTo("23:59:59");
    }
}
