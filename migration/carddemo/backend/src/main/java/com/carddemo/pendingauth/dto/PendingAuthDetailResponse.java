package com.carddemo.pendingauth.dto;

/**
 * The CPVD (COPAU1A) screen state, field for field as POPULATE-AUTH-DETAILS
 * builds it (COPAUS1C.cbl:291-357).
 *
 * <p>When the account id or the selected key is missing/invalid COPAUS1C sets
 * the error flag and POPULATE-AUTH-DETAILS moves nothing, so the map stays
 * empty and no message is issued (quirk Q-2); {@link #empty(String)} is that
 * state.
 *
 * @param authKey    CDEMO-CPVD-PAU-SELECTED for the displayed authorization
 * @param cardNumber CARDNUMO
 * @param authDate   AUTHDTO, MM/DD/YY
 * @param authTime   AUTHTMO, HH:MM:SS
 * @param authAmount AUTHAMTO: PA-APPROVED-AMT through PIC -zzzzzzz9.99
 * @param authResponse AUTHRSPO: 'A' or 'D'
 * @param authReason AUTHRSNO: {@code cccc-DESCRIPTION} from the decline table
 * @param authCode   AUTHCDO: PA-PROCESSING-CODE (quirk Q-3)
 * @param posEntryMode POSEMDO
 * @param authSource AUTHSRCO
 * @param mccCode    MCCCDO
 * @param cardExpiry CRDEXPO, YY/MM
 * @param authType   AUTHTYPO
 * @param transactionId TRNIDO
 * @param matchStatus AUTHMTCO
 * @param fraud      AUTHFRDO: {@code F-yyyymmdd} / {@code R-yyyymmdd}, else {@code -}
 */
public record PendingAuthDetailResponse(String authKey,
                                        String cardNumber,
                                        String authDate,
                                        String authTime,
                                        String authAmount,
                                        String authResponse,
                                        String authReason,
                                        String authCode,
                                        String posEntryMode,
                                        String authSource,
                                        String mccCode,
                                        String cardExpiry,
                                        String authType,
                                        String transactionId,
                                        String matchStatus,
                                        String merchantName,
                                        String merchantId,
                                        String merchantCity,
                                        String merchantState,
                                        String merchantZip,
                                        String fraud,
                                        boolean found,
                                        String message) {

    /** The blank detail area COPAUS1C leaves behind when ERR-FLG is on. */
    public static PendingAuthDetailResponse empty(String message) {
        return new PendingAuthDetailResponse(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                false, message);
    }

    /** The same detail with a different message line (fraud toggle, PF8 at end). */
    public PendingAuthDetailResponse withMessage(String newMessage) {
        return new PendingAuthDetailResponse(authKey, cardNumber, authDate, authTime, authAmount,
                authResponse, authReason, authCode, posEntryMode, authSource, mccCode, cardExpiry,
                authType, transactionId, matchStatus, merchantName, merchantId, merchantCity,
                merchantState, merchantZip, fraud, found, newMessage);
    }
}
