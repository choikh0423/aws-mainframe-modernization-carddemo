package com.carddemo.pendingauth.service;

import com.carddemo.common.domain.PendingAuthDetailRecord;
import com.carddemo.common.domain.PendingAuthSummaryRecord;
import com.carddemo.common.repository.PendingAuthDetailRepository;
import com.carddemo.common.repository.PendingAuthSummaryRepository;
import com.carddemo.pendingauth.repository.PendingAuthDetailBrowseRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CP00 / COPAUA0C: FR-P1..FR-P9, one JMS message per {@code process(...)} call.
 *
 * <p>Runs against its own in-memory database because it writes PAUTSUM0 and
 * PAUTDTL1 segments. The clock stands at 2025-01-15 15:00:00 UTC and ticks a
 * millisecond per reading, which is what the CICS ASKTIME of two consecutive
 * messages does and what keeps their PAUT9CTS keys distinct.
 *
 * <p>The methods run in order: the declined-amount quirk (Q-6) is a property of
 * the sequence of messages a run processes, not of a single message.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-cp00;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthorizationDriverServiceTest {

    /** 2025-01-15 15:00:00 UTC: PAUT9CTS date part 99999 - 25015 = 74984. */
    static final Instant NOW = Instant.parse("2025-01-15T15:00:00.000Z");

    /** {@code EXEC CICS ASKTIME}: a new reading, a millisecond later. */
    static final class TickingClock extends Clock {
        private final AtomicLong millis = new AtomicLong(NOW.toEpochMilli());

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.getAndIncrement());
        }
    }

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return new TickingClock();
        }
    }

    private static final String CARD_ACCT_1 = "9680294154603697";

    @Autowired
    private AuthorizationDriverService driver;
    @Autowired
    private PendingAuthSummaryRepository summaries;
    @Autowired
    private PendingAuthDetailRepository detailCrud;
    @Autowired
    private PendingAuthDetailBrowseRepository details;

    /** Start from an empty DBPAUTP0, the state CP00 opens accounts in. */
    @BeforeAll
    void emptyTheHierarchy() {
        detailCrud.deleteAllInBatch();
        summaries.deleteAllInBatch();
    }

    private static String request(String card, String amount, String transactionId) {
        return String.join(",",
                "250115", "150000", card, "0100", "2605", "0100", "POS",
                "001000", amount, "5411", "840", "05", "900000001",
                "ACME SUPERMARKET", "NEW YORK", "NY", "10001", transactionId);
    }

    @Test
    @Order(1)
    void frP1_anUnknownCardIsDeclined3100AndWritesNothing() {
        String reply = driver.process(request("9999999999999999", "+00000013.75", "PAUTHX00000001"));

        assertThat(reply)
                .isEqualTo("9999999999999999,PAUTHX00000001 ,150000,05,3100,          0.00,");
        assertThat(summaries.count()).isZero();
        assertThat(details.count()).isZero();
    }

    @Test
    @Order(2)
    void frP2_anApprovalRepliesOoAndOpensTheSummaryAndDetailSegments() {
        String reply = driver.process(request(CARD_ACCT_1, "+00000013.75", "PAUTHX00000002"));

        // CCPAURLY: card, transaction id, auth id code, response, reason, amount,
        // each followed by a comma — the STRING leaves a trailing one (quirk Q-9).
        assertThat(reply).isEqualTo(CARD_ACCT_1 + ",PAUTHX00000002 ,150000,00,0000,"
                + "         13.75,");

        PendingAuthSummaryRecord summary = summaries.findById(1L).orElseThrow();
        assertThat(summary.getPaCustId()).isEqualTo(1L);
        assertThat(summary.getPaApprovedAuthCnt()).isEqualTo(1);
        assertThat(summary.getPaDeclinedAuthCnt()).isZero();
        assertThat(summary.getPaApprovedAuthAmt()).isEqualByComparingTo(new BigDecimal("13.75"));
        assertThat(summary.getPaCreditBalance()).isEqualByComparingTo(new BigDecimal("13.75"));
        assertThat(summary.getPaCreditLimit()).isEqualByComparingTo(new BigDecimal("2020.00"));

        List<PendingAuthDetailRecord> children = details.browseChildren(1L);
        assertThat(children).hasSize(1);
        PendingAuthDetailRecord d = children.get(0);
        assertThat(d.getId().getPaAuthDate9c()).isEqualTo(74984);
        // 999999999 - 15:00:00.00x
        assertThat(d.getId().getPaAuthTime9c()).isBetween(849999990L, 849999999L);
        assertThat(d.getPaAuthOrigDate()).isEqualTo("250115");
        assertThat(d.getPaAuthOrigTime()).isEqualTo("150000");
        assertThat(d.getPaCardNum()).isEqualTo(CARD_ACCT_1);
        assertThat(d.getPaAuthIdCode()).isEqualTo("150000");
        assertThat(d.getPaAuthRespCode()).isEqualTo("00");
        assertThat(d.getPaAuthRespReason()).isEqualTo("0000");
        assertThat(d.getPaProcessingCode()).isEqualTo(1000L);
        assertThat(d.getPaTransactionAmt()).isEqualByComparingTo(new BigDecimal("13.75"));
        assertThat(d.getPaApprovedAmt()).isEqualByComparingTo(new BigDecimal("13.75"));
        assertThat(d.getPaPosEntryMode()).isEqualTo(5);
        assertThat(d.getPaMerchantName()).isEqualTo("ACME SUPERMARKET");
        assertThat(d.getPaTransactionId()).isEqualTo("PAUTHX00000002");
        assertThat(d.getPaMatchStatus()).isEqualTo("P");
    }

    @Test
    @Order(3)
    void frP3_anAmountOverTheRemainingLimitIsDeclined4100() {
        // The summary now exists, so the decision uses PA-CREDIT-LIMIT - PA-CREDIT-BALANCE.
        String reply = driver.process(request(CARD_ACCT_1, "+00009000.00", "PAUTHX00000003"));

        assertThat(reply).endsWith(",05,4100,          0.00,");

        PendingAuthSummaryRecord summary = summaries.findById(1L).orElseThrow();
        assertThat(summary.getPaDeclinedAuthCnt()).isEqualTo(1);
        // Quirk Q-6: 8400 accumulates the detail work area's PA-TRANSACTION-AMT,
        // which still holds the *previous* message's amount.
        assertThat(summary.getPaDeclinedAuthAmt()).isEqualByComparingTo(new BigDecimal("13.75"));
        assertThat(summary.getPaCreditBalance()).isEqualByComparingTo(new BigDecimal("13.75"));

        PendingAuthDetailRecord declined = details.browseChildren(1L).stream()
                .filter(d -> "PAUTHX00000003".equals(d.getPaTransactionId()))
                .findFirst().orElseThrow();
        assertThat(declined.getPaAuthRespCode()).isEqualTo("05");
        assertThat(declined.getPaAuthRespReason()).isEqualTo("4100");
        assertThat(declined.getPaApprovedAmt()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(declined.getPaMatchStatus()).isEqualTo("D");
    }

    @Test
    @Order(4)
    void frP4_theDeclinedAmountCarriesForwardFromThePreviousMessage() {
        driver.process(request(CARD_ACCT_1, "+00000100.00", "PAUTHX00000004"));
        BigDecimal before = summaries.findById(1L).orElseThrow().getPaDeclinedAuthAmt();

        driver.process(request(CARD_ACCT_1, "+00009000.00", "PAUTHX00000005"));

        assertThat(summaries.findById(1L).orElseThrow().getPaDeclinedAuthAmt())
                .isEqualByComparingTo(before.add(new BigDecimal("100.00")));
    }

    @Test
    @Order(5)
    void frP5_numvalAmountsAndShortMessagesAreTakenAsTheUnstringTakesThem() {
        String reply = driver.process(request(CARD_ACCT_1, "  13.75  ", "PAUTHX00000006"));
        assertThat(reply).endsWith(",00,0000,         13.75,");

        // A short message leaves the tail fields blank; the card is still field 3.
        String shortReply = driver.process("250115,150000," + CARD_ACCT_1);
        assertThat(shortReply).startsWith(CARD_ACCT_1 + ",               ,150000,00,0000,");
        assertThat(shortReply).endsWith(",          0.00,");
    }
}
