package com.carddemo.common.batch;

/**
 * ABEND-DATA (app/cpy/CSMSG02Y.cpy): the four fields a batch program fills in
 * before it hands control to CEE3ABD.
 *
 * @param abendCode    ABEND-CODE, PIC X(4)
 * @param abendCulprit ABEND-CULPRIT, PIC X(8) - the program that failed
 * @param abendReason  ABEND-REASON, PIC X(50)
 * @param abendMsg     ABEND-MSG, PIC X(72)
 */
public record AbendData(String abendCode, String abendCulprit, String abendReason, String abendMsg) {

    /** Formats the four fields the way the COBOL DISPLAY statements emit them. */
    @Override
    public String toString() {
        return "ABEND-CODE=" + abendCode
                + " ABEND-CULPRIT=" + abendCulprit
                + " ABEND-REASON=" + abendReason
                + " ABEND-MSG=" + abendMsg;
    }
}
