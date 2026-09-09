package com.carddemo.batch.intcalc;

import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.TransactionRecord;

import java.util.List;

/**
 * What CBACT04C produces for one account: the interest transactions written to the
 * SYSTRAN generation (CBACT04C.cbl:473-515) and, unless this is the final account
 * of the run (FR-I16), the rewritten account row (CBACT04C.cbl:350-370).
 *
 * @param accountUpdate the account to rewrite, or {@code null} when the COBOL never
 *                      reaches {@code 1050-UPDATE-ACCOUNT} for it
 * @param transactions  the generated transactions, in the order they are written
 */
record InterestPosting(AccountRecord accountUpdate, List<TransactionRecord> transactions) {
}
