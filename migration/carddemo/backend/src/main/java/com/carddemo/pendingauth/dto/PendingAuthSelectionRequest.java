package com.carddemo.pendingauth.dto;

import java.util.List;

/**
 * The CPVS map fields COPAUS0C's PROCESS-ENTER-KEY reads back
 * (COPAUS0C.cbl:262-341): the account id and the five selection flags with the
 * authorization keys the screen was showing ({@code CDEMO-CPVS-AUTH-KEYS}).
 *
 * @param acctId ACCTIDI
 * @param rows   SEL0001I..SEL0005I paired with their row key, in screen order
 */
public record PendingAuthSelectionRequest(String acctId, List<Row> rows) {

    public record Row(String flag, String authKey) {
    }
}
