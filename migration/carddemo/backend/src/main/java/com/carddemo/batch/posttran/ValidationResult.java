package com.carddemo.batch.posttran;

import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CardXrefRecord;

/**
 * Outcome of {@code 1500-VALIDATE-TRAN} for one daily transaction: the contents
 * of {@code WS-VALIDATION-TRAILER} (CBTRN02C.cbl:180-182) plus the two records
 * the validation read, which {@code 2000-POST-TRANSACTION} goes on to use.
 *
 * @param reason      {@code WS-VALIDATION-FAIL-REASON}; 0 means the record posts
 * @param description {@code WS-VALIDATION-FAIL-REASON-DESC}
 * @param xref        the CCXREF record, null when the card number was unknown
 * @param account     the ACCTDAT record, null when it was not read or not found
 */
public record ValidationResult(int reason,
                               String description,
                               CardXrefRecord xref,
                               AccountRecord account) {

    /** Reason 0 with spaces, as the main loop resets it before every record (CBTRN02C.cbl:208-209). */
    public static ValidationResult accepted(CardXrefRecord xref, AccountRecord account) {
        return new ValidationResult(0, "", xref, account);
    }

    public static ValidationResult rejected(int reason, String description,
                                            CardXrefRecord xref, AccountRecord account) {
        return new ValidationResult(reason, description, xref, account);
    }

    public boolean isRejected() {
        return reason != 0;
    }
}
