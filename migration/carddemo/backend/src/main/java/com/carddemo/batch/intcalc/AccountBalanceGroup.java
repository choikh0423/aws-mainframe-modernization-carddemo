package com.carddemo.batch.intcalc;

import com.carddemo.common.domain.TransactionCategoryBalanceRecord;

import java.util.List;

/**
 * All TCATBALF rows of one account, in key order — the unit CBACT04C works on
 * between two account breaks (CBACT04C.cbl:194-206).
 *
 * @param acctId         TRANCAT-ACCT-ID shared by every row
 * @param balances       the account's category balances, in (type, category) order
 * @param lastGroupInRun true when no further account follows in TCATBALF, which is
 *                       what makes {@code 1050-UPDATE-ACCOUNT} unreachable for this
 *                       account (CBACT04C.cbl:188-191,219-220 — quirk FR-I16)
 */
record AccountBalanceGroup(long acctId,
                           List<TransactionCategoryBalanceRecord> balances,
                           boolean lastGroupInRun) {
}
