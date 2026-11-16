package com.carddemo.statements.dto;

import java.util.List;

/**
 * One page (10 rows, newest cycle first) of an account's statements. {@code page} is 0-based.
 */
public record StatementPageResponse(String acctId, int page, int pageSize, long totalElements,
                                    int totalPages, boolean hasNext, boolean hasPrevious,
                                    List<StatementSummary> statements) {
}
