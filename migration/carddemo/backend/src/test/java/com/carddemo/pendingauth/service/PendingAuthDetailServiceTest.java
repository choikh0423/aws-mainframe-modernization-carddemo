package com.carddemo.pendingauth.service;

import com.carddemo.common.domain.AuthFraudId;
import com.carddemo.common.domain.AuthFraudRecord;
import com.carddemo.common.domain.PendingAuthDetailId;
import com.carddemo.common.repository.AuthFraudRepository;
import com.carddemo.common.repository.PendingAuthDetailRepository;
import com.carddemo.pendingauth.dto.PendingAuthDetailResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CPVD / COPAUS1C and the COPAUS2C fraud module: FR-D1..FR-D6 and FR-C1..FR-C4.
 *
 * <p>{@code @Transactional} rolls the fraud writes back so the seeded demo
 * hierarchy stays as the other tests in the shared context expect it.
 */
@SpringBootTest
@Transactional
class PendingAuthDetailServiceTest {

    private static final String FIRST_KEY = "74984849999999";
    private static final String SECOND_KEY = "74984859999999";
    private static final String LAST_KEY = "74984909999999";
    private static final String ONLY_KEY_ACCT_2 = "74984879999999";

    @Autowired
    private PendingAuthDetailService service;
    @Autowired
    private AuthFraudRepository authFraudRepository;
    @Autowired
    private PendingAuthDetailRepository detailRepository;

    @Test
    void frD1_theSelectedAuthorizationIsRenderedFieldForField() {
        PendingAuthDetailResponse detail = service.view("1", FIRST_KEY);

        assertThat(detail.found()).isTrue();
        assertThat(detail.authKey()).isEqualTo(FIRST_KEY);
        assertThat(detail.cardNumber()).isEqualTo("9680294154603697");
        assertThat(detail.authDate()).isEqualTo("01/15/25");
        assertThat(detail.authTime()).isEqualTo("15:00:00");
        // AUTHAMTO carries PA-APPROVED-AMT (quirk Q-1).
        assertThat(detail.authAmount()).isEqualTo("       13.75");
        assertThat(detail.authResponse()).isEqualTo("A");
        assertThat(detail.authReason()).isEqualTo("0000-APPROVED");
        // AUTHCDO carries PA-PROCESSING-CODE, not PA-AUTH-ID-CODE (quirk Q-3).
        assertThat(detail.authCode()).isEqualTo("001000");
        assertThat(detail.posEntryMode()).isEqualTo("05");
        assertThat(detail.authSource()).isEqualTo("POS   ");
        assertThat(detail.mccCode()).isEqualTo("5411");
        assertThat(detail.cardExpiry()).isEqualTo("26/05");
        assertThat(detail.authType()).isEqualTo("0100");
        assertThat(detail.transactionId()).isEqualTo("PAUTH0000000001");
        assertThat(detail.matchStatus()).isEqualTo("P");
        assertThat(detail.merchantName()).isEqualTo("ACME SUPERMARKET");
        assertThat(detail.merchantId()).isEqualTo("900000001");
        assertThat(detail.merchantCity()).isEqualTo("NEW YORK");
        assertThat(detail.merchantState()).isEqualTo("NY");
        assertThat(detail.merchantZip()).isEqualTo("10001");
        assertThat(detail.fraud()).isEqualTo("-");
        assertThat(detail.message()).isNull();
    }

    @Test
    void frD2_aDeclinedAuthorizationShowsItsReasonFromTheTable() {
        PendingAuthDetailResponse detail = service.view("1", "74984869999999");

        assertThat(detail.authResponse()).isEqualTo("D");
        assertThat(detail.authReason()).isEqualTo("4100-INSUFFICNT FUND");
        assertThat(PendingAuthDetailService.declineReason("5300")).isEqualTo("5300-LOST CARD");
        assertThat(PendingAuthDetailService.declineReason("7777")).isEqualTo("9999-ERROR");
        assertThat(PendingAuthDetailService.declineReason(null)).isEqualTo("9999-ERROR");
    }

    @Test
    void frD3_anInvalidAccountOrKeyLeavesTheDetailAreaBlankWithNoMessage() {
        for (PendingAuthDetailResponse blank : new PendingAuthDetailResponse[] {
                service.view("ABC", FIRST_KEY),
                service.view("", FIRST_KEY),
                service.view("1", "not-a-key"),
                service.view("1", null),
                service.view("1", "74984000000001"),
                service.view("99999999999", FIRST_KEY) }) {
            assertThat(blank.found()).isFalse();
            assertThat(blank.cardNumber()).isNull();
            assertThat(blank.message()).isNull();
        }
    }

    @Test
    void frD4_pf8WalksToTheNextChildAndStopsAtTheEndOfTheChain() {
        assertThat(service.next("1", FIRST_KEY).transactionId()).isEqualTo("PAUTH0000000002");
        assertThat(service.next("1", SECOND_KEY).transactionId()).isEqualTo("PAUTH0000000003");

        PendingAuthDetailResponse end = service.next("1", LAST_KEY);
        assertThat(end.transactionId()).isEqualTo("PAUTH0000000007");
        assertThat(end.message()).isEqualTo("Already at the last Authorization...");

        PendingAuthDetailResponse only = service.next("2", ONLY_KEY_ACCT_2);
        assertThat(only.transactionId()).isEqualTo("PAUTH0000000008");
        assertThat(only.message()).isEqualTo("Already at the last Authorization...");
    }

    @Test
    void frD5_pf5MarksFraudAndFrC1_thePairIsInsertedIntoAuthfrds() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yy"));

        PendingAuthDetailResponse marked = service.markFraud("1", FIRST_KEY);

        assertThat(marked.message()).isEqualTo("AUTH MARKED FRAUD...");
        assertThat(marked.fraud()).isEqualTo("F-" + today);

        AuthFraudRecord row = authFraudRepository
                .findById(new AuthFraudId("9680294154603697",
                        LocalDateTime.of(2025, 1, 15, 15, 0, 0)))
                .orElseThrow();
        assertThat(row.getAcctId()).isEqualTo(1L);
        assertThat(row.getCustId()).isEqualTo(1L);
        assertThat(row.getAuthType()).isEqualTo("0100");
        assertThat(row.getCardExpiryDate()).isEqualTo("2605");
        assertThat(row.getMessageType()).isEqualTo("0100");
        assertThat(row.getMessageSource()).isEqualTo("POS   ");
        assertThat(row.getAuthIdCode()).isEqualTo("150000");
        assertThat(row.getAuthRespCode()).isEqualTo("00");
        assertThat(row.getAuthRespReason()).isEqualTo("0000");
        assertThat(row.getProcessingCode()).isEqualTo("001000");
        assertThat(row.getTransactionAmt()).isEqualByComparingTo(new BigDecimal("13.75"));
        assertThat(row.getApprovedAmt()).isEqualByComparingTo(new BigDecimal("13.75"));
        assertThat(row.getMerchantCatagoryCode()).isEqualTo("5411");
        assertThat(row.getAcqrCountryCode()).isEqualTo("840");
        assertThat(row.getPosEntryMode()).isEqualTo(5);
        assertThat(row.getMerchantId()).isEqualTo("900000001");
        assertThat(row.getMerchantName()).isEqualTo("ACME SUPERMARKET");
        assertThat(row.getMerchantCity()).isEqualTo("NEW YORK");
        assertThat(row.getMerchantState()).isEqualTo("NY");
        assertThat(row.getMerchantZip()).isEqualTo("10001");
        assertThat(row.getTransactionId()).isEqualTo("PAUTH0000000001");
        assertThat(row.getMatchStatus()).isEqualTo("P");
        assertThat(row.getAuthFraud()).isEqualTo("F");
        assertThat(row.getFraudRptDate()).isEqualTo(LocalDate.now());

        assertThat(detailRepository
                .findById(new PendingAuthDetailId(1L, 74984, 849999999L))
                .orElseThrow().getPaAuthFraud()).isEqualTo("F");
    }

    @Test
    void frD6_asecondPf5RemovesTheFraudMarkAndFrC2_updatesTheExistingRow() {
        service.markFraud("1", FIRST_KEY);
        long rowsAfterFirst = authFraudRepository.count();

        PendingAuthDetailResponse removed = service.markFraud("1", FIRST_KEY);

        assertThat(removed.message()).isEqualTo("AUTH FRAUD REMOVED...");
        assertThat(removed.fraud()).startsWith("R-");
        assertThat(authFraudRepository.count()).isEqualTo(rowsAfterFirst);
        assertThat(authFraudRepository
                .findById(new AuthFraudId("9680294154603697",
                        LocalDateTime.of(2025, 1, 15, 15, 0, 0)))
                .orElseThrow().getAuthFraud()).isEqualTo("R");
    }

    @Test
    void frC3_theAuthTimestampIsRebuiltFromTheOriginalDateAndTheDeInvertedTime() {
        assertThat(AuthFraudService.authTimestamp(
                detailRepository.findById(new PendingAuthDetailId(1L, 74984, 849999999L))
                        .orElseThrow()))
                .isEqualTo(LocalDateTime.of(2025, 1, 15, 15, 0, 0));
    }

    @Test
    void frC4_fraudOnAnUnknownAuthorizationLeavesTheScreenBlank() {
        PendingAuthDetailResponse blank = service.markFraud("1", "74984000000001");

        assertThat(blank.found()).isFalse();
        assertThat(blank.message()).isNull();
    }
}
