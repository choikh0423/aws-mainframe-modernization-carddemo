package com.carddemo.pendingauth.dto;

/**
 * The outcome of PROCESS-ENTER-KEY's selection evaluation: either the
 * {@code XCTL} to COPAUS1C/CPVD with the selected key, or the message CPVS
 * leaves on the screen (COPAUS0C.cbl:308-341).
 *
 * @param selected   true when the legacy program would XCTL to CPVD
 * @param nextProgram COPAUS1C when selected, else null
 * @param nextTranId CPVD when selected, else null
 * @param authKey    CDEMO-CPVS-PAU-SELECTED
 * @param message    the CPVS message line
 */
public record PendingAuthSelectionResponse(boolean selected,
                                           String nextProgram,
                                           String nextTranId,
                                           String authKey,
                                           String message) {

    public static PendingAuthSelectionResponse xctl(String authKey) {
        return new PendingAuthSelectionResponse(true, "COPAUS1C", "CPVD", authKey, null);
    }

    public static PendingAuthSelectionResponse stay(String message) {
        return new PendingAuthSelectionResponse(false, null, null, null, message);
    }
}
