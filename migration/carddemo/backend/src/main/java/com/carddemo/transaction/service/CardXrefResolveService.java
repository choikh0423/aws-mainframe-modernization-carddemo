package com.carddemo.transaction.service;

import com.carddemo.transaction.dto.CardXrefResolveResponse;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.transaction.exception.CardXrefNotFoundException;
import com.carddemo.transaction.exception.TransactionValidationException;
import com.carddemo.common.repository.CardXrefRepository;
import org.springframework.stereotype.Service;

/**
 * CT02 account/card cross-reference resolution — a 1:1 port of COTRN02C's
 * VALIDATE-INPUT-KEY-FIELDS (COTRN02C.cbl:193-230) plus READ-CXACAIX-FILE
 * (cbl:576-604) and READ-CCXREF-FILE (cbl:609-637).
 *
 * <p>The legacy EVALUATE prefers the Account ID: if an Account ID is present it
 * is validated numeric and used to read the CXACAIX alternate index, and the
 * resulting card overwrites the card field (FR-A1); otherwise, if a Card Number
 * is present it is validated numeric and used to read the CCXREF base cluster to
 * fill the account field (FR-A2); if neither is present the user sees "Account
 * or Card Number must be entered..." (FR-A3). A NOTFND yields "Account ID NOT
 * found..." / "Card Number NOT found..." (FR-A4/FR-A5).
 */
@Service
public class CardXrefResolveService {

    public static final String BOTH_EMPTY = "Account or Card Number must be entered...";
    public static final String ACCOUNT_NUMERIC = "Account ID must be Numeric...";
    public static final String CARD_NUMERIC = "Card Number must be Numeric...";

    private static final int ACCT_ID_LEN = 11;   // XREF-ACCT-ID 9(11)
    private static final int CARD_NUM_LEN = 16;   // XREF-CARD-NUM X(16)

    private final CardXrefRepository cardXrefRepository;

    public CardXrefResolveService(CardXrefRepository cardXrefRepository) {
        this.cardXrefRepository = cardXrefRepository;
    }

    /**
     * Resolve the account/card pair for the CT02 add flow.
     *
     * @param accountId the ACTIDIN field (may be blank)
     * @param cardNumber the CARDNIN field (may be blank)
     * @return both keys zero-padded to their legacy widths
     */
    public CardXrefResolveResponse resolve(String accountId, String cardNumber) {
        String acct = accountId == null ? "" : accountId.trim();
        String card = cardNumber == null ? "" : cardNumber.trim();

        if (!acct.isEmpty()) {
            // WHEN ACTIDINI NOT = SPACES (COTRN02C.cbl:196-209): account drives the read.
            if (!isNumeric(acct)) {
                throw new TransactionValidationException(ACCOUNT_NUMERIC);
            }
            long acctId = Long.parseLong(acct);
            CardXrefRecord xref = cardXrefRepository.findByAcctId(acctId)
                    .orElseThrow(() -> new CardXrefNotFoundException(
                            CardXrefNotFoundException.ACCOUNT_NOT_FOUND));
            return new CardXrefResolveResponse(padNumeric(acct, ACCT_ID_LEN), xref.getCardNum());
        }

        if (!card.isEmpty()) {
            // WHEN CARDNINI NOT = SPACES (COTRN02C.cbl:210-223): card drives the read.
            if (!isNumeric(card)) {
                throw new TransactionValidationException(CARD_NUMERIC);
            }
            String cardKey = padNumeric(card, CARD_NUM_LEN);
            CardXrefRecord xref = cardXrefRepository.findById(cardKey)
                    .orElseThrow(() -> new CardXrefNotFoundException(
                            CardXrefNotFoundException.CARD_NOT_FOUND));
            return new CardXrefResolveResponse(padNumeric(String.valueOf(xref.getAcctId()), ACCT_ID_LEN),
                    xref.getCardNum());
        }

        // WHEN OTHER (COTRN02C.cbl:224-229): neither key entered.
        throw new TransactionValidationException(BOTH_EMPTY);
    }

    private boolean isNumeric(String v) {
        return !v.isEmpty() && v.chars().allMatch(Character::isDigit);
    }

    /**
     * Left-pad a numeric key to its VSAM width, mirroring COBOL's numeric->X
     * MOVE (e.g. WS-CARD-NUM-N 9(16) -> XREF-CARD-NUM X(16), COTRN02C.cbl:220).
     */
    private String padNumeric(String numeric, int width) {
        if (numeric.length() >= width) {
            return numeric;
        }
        StringBuilder sb = new StringBuilder(width);
        for (int i = numeric.length(); i < width; i++) {
            sb.append('0');
        }
        return sb.append(numeric).toString();
    }
}
