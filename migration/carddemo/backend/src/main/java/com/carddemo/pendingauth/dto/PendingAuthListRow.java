package com.carddemo.pendingauth.dto;

import com.carddemo.common.domain.PendingAuthDetailRecord;
import com.carddemo.pendingauth.util.PendingAuthFormat;
import com.carddemo.pendingauth.util.PendingAuthKey;

/**
 * One CPVS list line, exactly the fields POPULATE-AUTH-LIST moves onto a
 * COPAU0A row (COPAUS0C.cbl:521-545): TRNIDnn, PDATEnn, PTIMEnn, PTYPEnn,
 * PAPRVnn, PSTATnn, PAMTnnn — plus the row's authorization key, which the
 * legacy screen kept invisibly in {@code CDEMO-CPVS-AUTH-KEYS}.
 *
 * @param authKey        CDEMO-CPVS-AUTH-KEYS(n): the selection key handed to CPVD
 * @param transactionId  TRNIDnn
 * @param date           PDATEnn, MM/DD/YY
 * @param time           PTIMEnn, HH:MM:SS
 * @param authType       PTYPEnn
 * @param approvalStatus PAPRVnn: 'A' when PA-AUTH-RESP-CODE = '00', else 'D'
 * @param matchStatus    PSTATnn
 * @param amountDisplay  PAMTnnn: PA-APPROVED-AMT through PIC -zzzzzzz9.99
 */
public record PendingAuthListRow(String authKey,
                                 String transactionId,
                                 String date,
                                 String time,
                                 String authType,
                                 String approvalStatus,
                                 String matchStatus,
                                 String amountDisplay) {

    public static PendingAuthListRow from(PendingAuthDetailRecord d) {
        return new PendingAuthListRow(
                PendingAuthKey.of(d.getId()),
                d.getPaTransactionId(),
                PendingAuthFormat.displayDate(d.getPaAuthOrigDate()),
                PendingAuthFormat.displayTime(d.getPaAuthOrigTime()),
                d.getPaAuthType(),
                "00".equals(d.getPaAuthRespCode()) ? "A" : "D",
                d.getPaMatchStatus(),
                PendingAuthFormat.amount12(d.getPaApprovedAmt()));
    }
}
