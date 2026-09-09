package com.carddemo.mqinquiry.message;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-MQI-001, FR-MQI-002: the 1000-byte request and reply buffers. */
class InquiryRequestMessageTest {

    @Test
    void parsesTheThreeFieldsOfTheRequestCopy() {
        InquiryRequestMessage request = InquiryRequestMessage.parse("INQA00000000001");

        assertThat(request.func()).isEqualTo("INQA");
        assertThat(request.key()).isEqualTo("00000000001");
        assertThat(request.filler()).hasSize(985).isBlank();
        assertThat(request.keyAsNumber()).isEqualTo(1L);
        assertThat(request.keyIsPositive()).isTrue();
    }

    @Test
    void padsAShortPayloadAndTruncatesALongOne() {
        assertThat(InquiryRequestMessage.render("INQA", 1L)).hasSize(1000);
        assertThat(InquiryRequestMessage.parse("INQA").key()).isBlank();
        assertThat(InquiryRequestMessage.parse("INQA00000000001" + "X".repeat(2000)).filler())
                .hasSize(985);
    }

    @Test
    void aKeyOfZeroOrNonDigitsIsNotPositiveAndReadsAsZero() {
        assertThat(InquiryRequestMessage.parse("INQA00000000000").keyIsPositive()).isFalse();
        assertThat(InquiryRequestMessage.parse("INQAABCDEFGHIJK").keyIsPositive()).isFalse();
        assertThat(InquiryRequestMessage.parse("INQAABCDEFGHIJK").keyAsNumber()).isZero();
    }

    @Test
    void picClausesPadAndSignTheWayCobolDoes() {
        assertThat(MqInquiryLayout.alphanumeric("AB", 4)).isEqualTo("AB  ");
        assertThat(MqInquiryLayout.alphanumeric("ABCDE", 4)).isEqualTo("ABCD");
        assertThat(MqInquiryLayout.unsigned(1L, 11)).isEqualTo("00000000001");
        assertThat(MqInquiryLayout.signed(new BigDecimal("194.00"), 10, 2)).isEqualTo("00000001940{");
        assertThat(MqInquiryLayout.signed(new BigDecimal("-25.00"), 10, 2)).isEqualTo("00000000250}");
        assertThat(MqInquiryLayout.signed(BigDecimal.ZERO, 10, 2)).isEqualTo("00000000000{");
        assertThat(MqInquiryLayout.signed(new BigDecimal("13.75"), 10, 2)).isEqualTo("00000000137E");
        assertThat(MqInquiryLayout.signed(new BigDecimal("-13.75"), 10, 2)).isEqualTo("00000000137N");
        assertThat(MqInquiryLayout.message("X")).hasSize(1000).startsWith("X ");
    }
}
