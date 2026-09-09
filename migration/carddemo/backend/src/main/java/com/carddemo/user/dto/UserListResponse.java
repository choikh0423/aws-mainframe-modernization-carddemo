package com.carddemo.user.dto;

import java.util.List;

/**
 * One CU00 page: up to 10 rows plus the paging cursor COUSR00C kept in the
 * COMMAREA extension CDEMO-CU00-INFO (COUSR00C.cbl:67-76).
 *
 * <p>{@code firstId}/{@code lastId} are CDEMO-CU00-USRID-FIRST/LAST, the ids the
 * next PF7/PF8 request pages from; {@code hasNextPage} is the "peek one past the
 * page" flag CDEMO-CU00-NEXT-PAGE-FLG (cbl:305-312) that gates PF8 (FR-UL-6);
 * {@code hasPrevPage} is the PAGE-NUM &gt; 1 guard that gates PF7 (FR-UL-5).
 */
public class UserListResponse {

    private final List<UserListRow> rows;
    private final String firstId;
    private final String lastId;
    private final boolean hasNextPage;
    private final boolean hasPrevPage;
    private final int count;

    public UserListResponse(List<UserListRow> rows, String firstId, String lastId,
                            boolean hasNextPage, boolean hasPrevPage) {
        this.rows = rows;
        this.firstId = firstId;
        this.lastId = lastId;
        this.hasNextPage = hasNextPage;
        this.hasPrevPage = hasPrevPage;
        this.count = rows.size();
    }

    public List<UserListRow> getRows() { return rows; }
    public String getFirstId() { return firstId; }
    public String getLastId() { return lastId; }
    public boolean isHasNextPage() { return hasNextPage; }
    public boolean isHasPrevPage() { return hasPrevPage; }
    public int getCount() { return count; }
}
