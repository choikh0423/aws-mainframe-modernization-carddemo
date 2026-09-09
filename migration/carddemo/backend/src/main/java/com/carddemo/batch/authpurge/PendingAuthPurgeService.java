package com.carddemo.batch.authpurge;

import com.carddemo.common.domain.PendingAuthDetailRecord;
import com.carddemo.common.domain.PendingAuthSummaryRecord;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The decision half of CBPAUP0C: 4000-CHECK-IF-EXPIRED and the
 * summary-deletion test in MAIN-PARA, kept free of persistence so every rule is
 * unit-testable on its own.
 */
public final class PendingAuthPurgeService {

    private PendingAuthPurgeService() {
    }

    /**
     * What the purge decided for one root, and the counters as 4000 left them in
     * working storage. The four running values are returned rather than stored
     * because the program never writes the root back (quirk Q-8).
     *
     * @param expiredDetails the children that qualified for {@code DLET}
     * @param deleteSummary  whether MAIN-PARA's counter test deletes the root
     * @param approvedCnt    PA-APPROVED-AUTH-CNT after the child loop
     * @param declinedCnt    PA-DECLINED-AUTH-CNT after the child loop
     * @param approvedAmt    PA-APPROVED-AUTH-AMT after the child loop
     * @param declinedAmt    PA-DECLINED-AUTH-AMT after the child loop
     */
    public record Outcome(List<PendingAuthDetailRecord> expiredDetails,
                          boolean deleteSummary,
                          int approvedCnt,
                          int declinedCnt,
                          BigDecimal approvedAmt,
                          BigDecimal declinedAmt) {
    }

    /**
     * Runs one root's child loop.
     *
     * <p>{@code COMPUTE WS-AUTH-DATE = 99999 - PA-AUTH-DATE-9C} then
     * {@code WS-DAY-DIFF = CURRENT-YYDDD - WS-AUTH-DATE}, and a difference of at
     * least the expiry days qualifies the child for deletion
     * (CBPAUP0C.cbl:276-296). The arithmetic is plain {@code YYDDD} subtraction,
     * so an authorization from the previous year yields a large positive
     * difference and one from the next year a negative one; that is the legacy
     * behaviour and is preserved (quirk Q-10).
     *
     * <p>The counters (and, in the source, the approved/declined amounts) are
     * decremented in working storage and never written back, because the program
     * never {@code REPL}s the root (quirk Q-8): only the deletion test below
     * observes the new values.
     */
    public static Outcome purge(PendingAuthSummaryRecord summary,
                                List<PendingAuthDetailRecord> children,
                                int currentYyddd,
                                int expiryDays) {
        int approvedCnt = summary.getPaApprovedAuthCnt() == null ? 0 : summary.getPaApprovedAuthCnt();
        int declinedCnt = summary.getPaDeclinedAuthCnt() == null ? 0 : summary.getPaDeclinedAuthCnt();
        BigDecimal approvedAmt = nz(summary.getPaApprovedAuthAmt());
        BigDecimal declinedAmt = nz(summary.getPaDeclinedAuthAmt());
        List<PendingAuthDetailRecord> expired = new ArrayList<>();

        for (PendingAuthDetailRecord child : children) {
            int authDate = 99999 - child.getId().getPaAuthDate9c();
            int dayDiff = currentYyddd - authDate;
            if (dayDiff < expiryDays) {
                continue;
            }
            expired.add(child);
            if ("00".equals(child.getPaAuthRespCode())) {
                approvedCnt--;
                approvedAmt = approvedAmt.subtract(nz(child.getPaApprovedAmt()));
            } else {
                declinedCnt--;
                declinedAmt = declinedAmt.subtract(nz(child.getPaTransactionAmt()));
            }
        }

        // IF PA-APPROVED-AUTH-CNT <= 0 AND PA-APPROVED-AUTH-CNT <= 0 — the
        // approved count is tested twice and the declined count never is
        // (quirk Q-7, CBPAUP0C.cbl:156-158).
        boolean deleteSummary = approvedCnt <= 0 && approvedCnt <= 0;
        return new Outcome(expired, deleteSummary, approvedCnt, declinedCnt, approvedAmt,
                declinedAmt);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
