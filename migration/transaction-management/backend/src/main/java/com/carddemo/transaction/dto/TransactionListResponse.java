package com.carddemo.transaction.dto;

import java.util.List;

/**
 * A single CT00 page: up to 10 rows plus the paging cursor/boundary metadata the
 * legacy COTRN00C kept in COMMAREA field CDEMO-CT00-INFO (COTRN00C.cbl:62-70).
 *
 * The CICS pseudo-conversation is stateless here — instead of a saved cursor the
 * client sends {@code startId}/{@code dir} and reads back:
 *   - {@code firstId}/{@code lastId} — CDEMO-CT00-TRNID-FIRST/LAST, the ids the
 *     next PF7/PF8 request pages from;
 *   - {@code hasNextPage} — the "peek one past the page" flag COTRN00C computes
 *     to decide whether PF8 advances (COTRN00C.cbl:305-320);
 *   - {@code hasPrevPage} — whether any row precedes the page (page > 1), the
 *     PF7 boundary guard (COTRN00C.cbl:245-252).
 */
public class TransactionListResponse {

    private final List<TransactionListRow> rows;
    private final String firstId;
    private final String lastId;
    private final boolean hasNextPage;
    private final boolean hasPrevPage;
    private final int count;

    public TransactionListResponse(List<TransactionListRow> rows, String firstId, String lastId,
                                   boolean hasNextPage, boolean hasPrevPage) {
        this.rows = rows;
        this.firstId = firstId;
        this.lastId = lastId;
        this.hasNextPage = hasNextPage;
        this.hasPrevPage = hasPrevPage;
        this.count = rows.size();
    }

    public List<TransactionListRow> getRows() { return rows; }
    public String getFirstId() { return firstId; }
    public String getLastId() { return lastId; }
    public boolean isHasNextPage() { return hasNextPage; }
    public boolean isHasPrevPage() { return hasPrevPage; }
    public int getCount() { return count; }
}
