package com.carddemo.batch.authpurge;

import com.carddemo.common.domain.PendingAuthDetailId;
import com.carddemo.common.domain.PendingAuthDetailRecord;
import com.carddemo.common.domain.PendingAuthSummaryRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CBPAUP0C: FR-B1..FR-B5 — the expiry test, the counter arithmetic and the
 * summary-deletion decision, all on the pure decision half of the job.
 */
class PendingAuthPurgeServiceTest {

    /** 2025-01-15. */
    private static final int TODAY = 25015;

    private static PendingAuthSummaryRecord summary(int approvedCnt, int declinedCnt,
                                                    String approvedAmt, String declinedAmt) {
        PendingAuthSummaryRecord s = new PendingAuthSummaryRecord();
        s.setPaAcctId(1L);
        s.setPaApprovedAuthCnt(approvedCnt);
        s.setPaDeclinedAuthCnt(declinedCnt);
        s.setPaApprovedAuthAmt(new BigDecimal(approvedAmt));
        s.setPaDeclinedAuthAmt(new BigDecimal(declinedAmt));
        return s;
    }

    private static PendingAuthDetailRecord child(int yyddd, String respCode, String amount) {
        PendingAuthDetailRecord d = new PendingAuthDetailRecord();
        d.setId(new PendingAuthDetailId(1L, 99999 - yyddd, 849999999L + yyddd));
        d.setPaAuthRespCode(respCode);
        d.setPaTransactionAmt(new BigDecimal(amount));
        d.setPaApprovedAmt("00".equals(respCode) ? new BigDecimal(amount) : BigDecimal.ZERO);
        return d;
    }

    @Test
    void frB1_aChildIsExpiredWhenTheDayDifferenceReachesTheExpiryDays() {
        List<PendingAuthDetailRecord> children = List.of(
                child(25009, "00", "10.00"),  // 6 days old  -> expired
                child(25010, "00", "20.00"),  // 5 days old  -> expired (>= 5)
                child(25011, "00", "30.00"),  // 4 days old  -> kept
                child(25015, "00", "40.00")); // today       -> kept

        PendingAuthPurgeService.Outcome outcome =
                PendingAuthPurgeService.purge(summary(4, 0, "100.00", "0.00"), children, TODAY, 5);

        assertThat(outcome.expiredDetails()).containsExactly(children.get(0), children.get(1));
    }

    @Test
    void frB2_expiredApprovalsDecrementTheApprovedCountAndAmount() {
        PendingAuthPurgeService.Outcome outcome = PendingAuthPurgeService.purge(
                summary(3, 0, "60.00", "0.00"),
                List.of(child(25001, "00", "10.00"), child(25002, "00", "20.00")),
                TODAY, 5);

        assertThat(outcome.approvedCnt()).isEqualTo(1);
        assertThat(outcome.approvedAmt()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(outcome.declinedCnt()).isZero();
        assertThat(outcome.deleteSummary()).isFalse();
    }

    @Test
    void frB3_expiredDeclinesDecrementTheDeclinedCountAndTheTransactionAmount() {
        PendingAuthPurgeService.Outcome outcome = PendingAuthPurgeService.purge(
                summary(0, 2, "0.00", "75.00"),
                List.of(child(25001, "05", "25.00")),
                TODAY, 5);

        assertThat(outcome.declinedCnt()).isEqualTo(1);
        assertThat(outcome.declinedAmt()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void frB4_theRootIsDeletedOnTheApprovedCountAloneEvenWithDeclinesLeft() {
        // Quirk Q-7: MAIN-PARA tests PA-APPROVED-AUTH-CNT twice and never tests
        // the declined count, so a root whose approvals are all gone is deleted
        // while unexpired declined children remain.
        PendingAuthPurgeService.Outcome outcome = PendingAuthPurgeService.purge(
                summary(1, 3, "10.00", "90.00"),
                List.of(child(25001, "00", "10.00"), child(25014, "05", "30.00")),
                TODAY, 5);

        assertThat(outcome.approvedCnt()).isZero();
        assertThat(outcome.declinedCnt()).isEqualTo(3);
        assertThat(outcome.deleteSummary()).isTrue();
        assertThat(outcome.expiredDetails()).hasSize(1);
    }

    @Test
    void frB5_theExpiryTestIsPlainYydddSubtraction() {
        // Quirk Q-10: WS-DAY-DIFF is a plain YYDDD subtraction, not a date
        // difference. A December 2024 authorization is 665 "days" old in
        // January 2025 and expires; one dated in 2026 is -986 and never does.
        PendingAuthPurgeService.Outcome yearBefore = PendingAuthPurgeService.purge(
                summary(1, 0, "10.00", "0.00"),
                List.of(child(24350, "00", "10.00")),
                TODAY, 5);
        assertThat(yearBefore.expiredDetails()).hasSize(1);
        assertThat(yearBefore.deleteSummary()).isTrue();

        PendingAuthPurgeService.Outcome yearAfter = PendingAuthPurgeService.purge(
                summary(1, 0, "10.00", "0.00"),
                List.of(child(26001, "00", "10.00")),
                TODAY, 5);
        assertThat(yearAfter.expiredDetails()).isEmpty();
        assertThat(yearAfter.approvedCnt()).isEqualTo(1);
        assertThat(yearAfter.deleteSummary()).isFalse();
    }
}
