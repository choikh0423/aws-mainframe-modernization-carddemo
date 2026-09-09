package com.carddemo.batch.posttran;

import com.carddemo.common.domain.DailyTransactionRecord;

/**
 * The 430-byte DALYREJS record of {@code 2500-WRITE-REJECT-REC}
 * (CBTRN02C.cbl:176-182, 446-451): the daily transaction exactly as it was read,
 * followed by {@code WS-VALIDATION-TRAILER}, itself a {@code PIC 9(04)} reason
 * code and a {@code PIC X(76)} description.
 *
 * <p>{@code POSTTRAN.jcl:36} allocates DALYREJS as {@code RECFM=F,LRECL=430}.
 */
public final class RejectRecord {

    public static final int RECORD_LENGTH = 430;
    public static final int TRAILER_LENGTH = 80;
    private static final int REASON_LENGTH = 4;
    private static final int DESCRIPTION_LENGTH = TRAILER_LENGTH - REASON_LENGTH;

    private RejectRecord() {
    }

    public static String render(DailyTransactionRecord daily, ValidationResult validation) {
        return DailyTransactionImage.render(daily) + trailer(validation);
    }

    static String trailer(ValidationResult validation) {
        return DailyTransactionImage.digits(validation.reason(), REASON_LENGTH)
                + DailyTransactionImage.text(validation.description(), DESCRIPTION_LENGTH);
    }
}
