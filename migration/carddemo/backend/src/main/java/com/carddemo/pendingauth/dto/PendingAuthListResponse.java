package com.carddemo.pendingauth.dto;

import java.util.List;

/**
 * The CPVS (COPAU0A) screen state: the account/customer header
 * (GATHER-ACCOUNT-DETAILS, COPAUS0C.cbl:749-807), up to five list rows, the
 * paging keys the legacy COMMAREA carried, and the message line.
 *
 * <p>{@code pageNum}, {@code firstKey}, {@code lastKey} and {@code nextPage}
 * are the stateless form of {@code CDEMO-CPVS-PAGE-NUM},
 * {@code CDEMO-CPVS-PAUKEY-PREV-PG}, {@code CDEMO-CPVS-PAUKEY-LAST} and
 * {@code CDEMO-CPVS-NEXT-PAGE-FLG} (boundary decision BD-1).
 */
public record PendingAuthListResponse(String acctId,
                                      String custId,
                                      String customerName,
                                      String addressLine1,
                                      String addressLine2,
                                      String phone,
                                      String creditLimit,
                                      String cashLimit,
                                      String creditBalance,
                                      String cashBalance,
                                      String approvedCount,
                                      String declinedCount,
                                      String approvedAmount,
                                      String declinedAmount,
                                      List<PendingAuthListRow> rows,
                                      int pageNum,
                                      String firstKey,
                                      String lastKey,
                                      boolean nextPage,
                                      String message) {

    /** The screen as it looks when only a message is shown (no account read). */
    public static PendingAuthListResponse message(String message) {
        return new PendingAuthListResponse(null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                List.of(), 0, null, null, false, message);
    }
}
