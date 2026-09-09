package com.carddemo.card.validator;

import com.carddemo.card.exception.CardValidationException;
import com.carddemo.card.message.CardManagementMessages;
import org.springframework.stereotype.Component;

/**
 * The account/card key edits shared by the three S-03 screens:
 * COCRDLIC 2210/2220-EDIT-ACCOUNT/CARD (filters, optional),
 * COCRDSLC 2210/2220 and COCRDUPC 1210/1220 (search keys, mandatory).
 *
 * <p>The map fields are PIC X(11) and X(16) and the programs test the whole
 * field with {@code IS NUMERIC}, so a short entry is space-padded and fails:
 * an account filter is valid only as exactly 11 digits, a card filter only as
 * exactly 16 digits. That is why the messages say "MUST BE A 11 DIGIT NUMBER".
 */
@Component
public class CardSearchKeyValidator {

    private static final int ACCT_LEN = 11;
    private static final int CARD_LEN = 16;

    /**
     * COBOL "not supplied": LOW-VALUES, SPACES or an all-zero numeric value
     * (COCRDLIC.cbl:1007-1013, 1042-1048).
     */
    public boolean isBlank(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || isAllZeroes(trimmed);
    }

    /**
     * COCRDSLC 1100-RECEIVE-MAP / COCRDUPC 1100-RECEIVE-MAP additionally treat a
     * lone '*' as "not supplied" (COCRDSLC.cbl:615-627, COCRDUPC.cbl:585-635) -
     * it is not a wildcard (quirk Q-6). The list screen has no such handling.
     */
    public boolean isBlankOrAsterisk(String value) {
        return isBlank(value) || "*".equals(value.trim());
    }

    /** CCLI account filter: blank means "no filter"; anything else must be 11 digits. */
    public String optionalAccountFilter(String value) {
        if (isBlank(value)) {
            return null;
        }
        if (!isNumeric(value, ACCT_LEN)) {
            throw new CardValidationException(CardManagementMessages.ACCOUNT_FILTER_NOT_NUMERIC, "acctId");
        }
        return value.trim();
    }

    /** CCLI card filter: blank means "no filter"; anything else must be 16 digits. */
    public String optionalCardFilter(String value) {
        if (isBlank(value)) {
            return null;
        }
        if (!isNumeric(value, CARD_LEN)) {
            throw new CardValidationException(CardManagementMessages.CARD_FILTER_NOT_NUMERIC, "cardNum");
        }
        return value.trim();
    }

    /**
     * CCDL/CCUP search keys: both are mandatory. Both blank gives
     * "No input received"; otherwise the missing or malformed one is reported
     * with the account message taking precedence over the card message
     * (COCRDSLC.cbl:637-720, COCRDUPC.cbl:645-799).
     */
    public void validateSearchKeys(String acctId, String cardNum) {
        boolean acctBlank = isBlankOrAsterisk(acctId);
        boolean cardBlank = isBlankOrAsterisk(cardNum);

        if (acctBlank && cardBlank) {
            throw new CardValidationException(CardManagementMessages.NO_INPUT_RECEIVED, "acctId", "cardNum");
        }
        if (acctBlank) {
            throw new CardValidationException(CardManagementMessages.ACCOUNT_NOT_PROVIDED, "acctId");
        }
        if (!isNumeric(acctId, ACCT_LEN)) {
            throw new CardValidationException(CardManagementMessages.ACCOUNT_FILTER_NOT_NUMERIC, "acctId");
        }
        if (cardBlank) {
            throw new CardValidationException(CardManagementMessages.CARD_NOT_PROVIDED, "cardNum");
        }
        if (!isNumeric(cardNum, CARD_LEN)) {
            throw new CardValidationException(CardManagementMessages.CARD_FILTER_NOT_NUMERIC, "cardNum");
        }
    }

    private boolean isNumeric(String value, int length) {
        String trimmed = value.trim();
        return trimmed.length() == length && trimmed.chars().allMatch(Character::isDigit);
    }

    private boolean isAllZeroes(String trimmed) {
        return trimmed.chars().allMatch(c -> c == '0');
    }
}
