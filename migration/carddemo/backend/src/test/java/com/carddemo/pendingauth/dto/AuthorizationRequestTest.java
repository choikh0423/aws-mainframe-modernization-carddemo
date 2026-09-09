package com.carddemo.pendingauth.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-A1/FR-A2: 2100-EXTRACT-REQUEST-MSG — the UNSTRING of CCPAURQY and the
 * NUMVAL conversion of the transaction amount.
 */
class AuthorizationRequestTest {

    private static final String MESSAGE = String.join(",",
            "250115", "150000", "9680294154603697", "0100", "2605", "0100", "POS",
            "001000", "+00000013.75", "5411", "840", "05", "900000001",
            "ACME SUPERMARKET", "NEW YORK", "NY", "10001", "PAUTH0000000001");

    @Test
    void frA1_allEighteenFieldsAreUnstrungInCopybookOrder() {
        AuthorizationRequest request = AuthorizationRequest.parse(MESSAGE);

        assertThat(request.authDate()).isEqualTo("250115");
        assertThat(request.authTime()).isEqualTo("150000");
        assertThat(request.cardNum()).isEqualTo("9680294154603697");
        assertThat(request.authType()).isEqualTo("0100");
        assertThat(request.cardExpiryDate()).isEqualTo("2605");
        assertThat(request.messageType()).isEqualTo("0100");
        assertThat(request.messageSource()).isEqualTo("POS");
        assertThat(request.processingCode()).isEqualTo("001000");
        assertThat(request.transactionAmt()).isEqualByComparingTo(new BigDecimal("13.75"));
        assertThat(request.merchantCategoryCode()).isEqualTo("5411");
        assertThat(request.acqrCountryCode()).isEqualTo("840");
        assertThat(request.posEntryMode()).isEqualTo("05");
        assertThat(request.merchantId()).isEqualTo("900000001");
        assertThat(request.merchantName()).isEqualTo("ACME SUPERMARKET");
        assertThat(request.merchantCity()).isEqualTo("NEW YORK");
        assertThat(request.merchantState()).isEqualTo("NY");
        assertThat(request.merchantZip()).isEqualTo("10001");
        assertThat(request.transactionId()).isEqualTo("PAUTH0000000001");
    }

    @Test
    void frA1_missingTrailingFieldsStayBlankAndSurplusFieldsAreIgnored() {
        AuthorizationRequest short_ = AuthorizationRequest.parse("250115,150000");
        assertThat(short_.cardNum()).isEmpty();
        assertThat(short_.transactionId()).isEmpty();
        assertThat(short_.transactionAmt()).isEqualByComparingTo(BigDecimal.ZERO);

        AuthorizationRequest long_ = AuthorizationRequest.parse(MESSAGE + ",SURPLUS");
        assertThat(long_.transactionId()).isEqualTo("PAUTH0000000001");
    }

    @Test
    void frA2_numvalTakesSignedSpacedAndUnpaddedAmounts() {
        assertThat(AuthorizationRequest.numval("+00000013.75"))
                .isEqualByComparingTo(new BigDecimal("13.75"));
        assertThat(AuthorizationRequest.numval("  13.75  "))
                .isEqualByComparingTo(new BigDecimal("13.75"));
        assertThat(AuthorizationRequest.numval("-13.75"))
                .isEqualByComparingTo(new BigDecimal("-13.75"));
        assertThat(AuthorizationRequest.numval("13.75-"))
                .isEqualByComparingTo(new BigDecimal("-13.75"));
        assertThat(AuthorizationRequest.numval("1,234.50"))
                .isEqualByComparingTo(new BigDecimal("1234.50"));
        assertThat(AuthorizationRequest.numval("")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(AuthorizationRequest.numval("ABC")).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
