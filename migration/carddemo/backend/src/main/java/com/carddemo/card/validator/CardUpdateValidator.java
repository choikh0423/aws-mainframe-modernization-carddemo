package com.carddemo.card.validator;

import com.carddemo.card.exception.CardValidationException;
import com.carddemo.card.message.CardManagementMessages;
import org.springframework.stereotype.Component;

/**
 * COCRDUPC 1230-EDIT-NAME, 1240-EDIT-CARDSTATUS, 1250-EDIT-EXPIRY-MON and
 * 1260-EDIT-EXPIRY-YEAR (COCRDUPC.cbl:806-943). The legacy program flags every
 * offending field but only the first message reaches the ERRMSG line
 * ({@code IF WS-RETURN-MSG-OFF}), so the edits run in the same order and the
 * first failure is thrown.
 */
@Component
public class CardUpdateValidator {

    /** 88 VALID-MONTH: 1 through 12 (COCRDUPC.cbl:92-95). */
    private static final int MIN_MONTH = 1;
    private static final int MAX_MONTH = 12;
    /** 88 VALID-YEAR: 1950 through 2099 (COCRDUPC.cbl:96-99). */
    private static final int MIN_YEAR = 1950;
    private static final int MAX_YEAR = 2099;

    public void validate(String name, String status, String expiryMonth, String expiryYear) {
        validateName(name);
        validateStatus(status);
        validateExpiryMonth(expiryMonth);
        validateExpiryYear(expiryYear);
    }

    /** Blank name, then "alphabets and spaces only" (COCRDUPC.cbl:806-841). */
    public void validateName(String name) {
        if (name == null || name.trim().isEmpty() || isAllZeroes(name.trim())) {
            throw new CardValidationException(CardManagementMessages.CARD_NAME_NOT_PROVIDED, "embossedName");
        }
        for (char c : name.toCharArray()) {
            if (!Character.isSpaceChar(c) && !isAsciiLetter(c)) {
                throw new CardValidationException(CardManagementMessages.CARD_NAME_NOT_ALPHA, "embossedName");
            }
        }
    }

    /** Only upper-case Y or N (88 FLG-YES-NO-VALID, COCRDUPC.cbl:91, 845-872). */
    public void validateStatus(String status) {
        if (status == null || !("Y".equals(status.trim()) || "N".equals(status.trim()))
                || status.trim().length() != 1) {
            throw new CardValidationException(CardManagementMessages.CARD_STATUS_NOT_YES_NO, "activeStatus");
        }
    }

    public void validateExpiryMonth(String expiryMonth) {
        Integer month = parse(expiryMonth);
        if (month == null || month < MIN_MONTH || month > MAX_MONTH) {
            throw new CardValidationException(CardManagementMessages.CARD_EXPIRY_MONTH_INVALID, "expiryMonth");
        }
    }

    public void validateExpiryYear(String expiryYear) {
        Integer year = parse(expiryYear);
        if (year == null || year < MIN_YEAR || year > MAX_YEAR) {
            throw new CardValidationException(CardManagementMessages.CARD_EXPIRY_YEAR_INVALID, "expiryYear");
        }
    }

    private Integer parse(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || !trimmed.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return Integer.valueOf(trimmed);
    }

    private boolean isAsciiLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private boolean isAllZeroes(String trimmed) {
        return trimmed.chars().allMatch(c -> c == '0');
    }
}
