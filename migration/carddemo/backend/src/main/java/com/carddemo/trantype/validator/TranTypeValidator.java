package com.carddemo.trantype.validator;

import com.carddemo.trantype.message.TranTypeMessages;
import org.springframework.stereotype.Component;

/**
 * The field edits CTLI and CTTU share, transcribed from the COBOL edit
 * paragraphs so both screens reject exactly what the legacy screens rejected.
 *
 * <ul>
 *   <li>{@code 1240-EDIT-ALPHANUM-REQD} (COTRTLIC.cbl:1181-1234) and
 *       {@code 1300-EDIT-ALPHANUM-REQD} (COTRTUPC.cbl:849-903) — required, and
 *       only letters, digits and spaces, checked by converting every letter and
 *       digit to a space and testing whether anything is left.</li>
 *   <li>{@code 1320-EDIT-NUM-REQD} (COTRTUPC.cbl:907-974) — required, numeric,
 *       not zero.</li>
 *   <li>{@code 1220-EDIT-TYPECD} (COTRTLIC.cbl:1096-1124) — the list filter,
 *       which is optional and only tested for being numeric.</li>
 * </ul>
 */
@Component
public class TranTypeValidator {

    /** COTRTUPC {@code 1000-CHECK-INPUTS}: `*` and spaces mean "not supplied". */
    public String normalizeInput(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return "*".equals(trimmed) ? "" : trimmed;
    }

    /** True for LOW-VALUES / SPACES after the `*` substitution above. */
    public boolean isBlank(String value) {
        return normalizeInput(value).isEmpty();
    }

    /**
     * {@code 1240-EDIT-ALPHANUM-REQD} on the 50-character description.
     *
     * @return the legacy error text, or {@code null} when the value is valid
     */
    public String validateDescription(String value) {
        String input = normalizeInput(value);
        if (input.isEmpty()) {
            return TranTypeMessages.DESCRIPTION_REQUIRED;
        }
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            boolean allowed = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == ' ';
            if (!allowed) {
                return TranTypeMessages.DESCRIPTION_NOT_ALPHANUM;
            }
        }
        return null;
    }

    /**
     * {@code 1320-EDIT-NUM-REQD} on the CTTU transaction-type key: supplied,
     * numeric and not zero.
     *
     * @return the legacy error text, or {@code null} when the value is valid
     */
    public String validateTypeCode(String value) {
        String input = normalizeInput(value);
        if (input.isEmpty()) {
            return TranTypeMessages.TYPE_CODE_REQUIRED;
        }
        if (!isNumeric(input)) {
            return TranTypeMessages.TYPE_CODE_NOT_NUMERIC;
        }
        if (Long.parseLong(input) == 0L) {
            return TranTypeMessages.TYPE_CODE_ZERO;
        }
        return null;
    }

    /**
     * COTRTUPC.cbl:834-842 — the key is moved through a numeric field and back,
     * so `7` is stored and looked up as `07` (FR-U07). Values longer than two
     * digits keep their low-order two digits, exactly as the {@code PIC 9(2)}
     * move truncates.
     */
    public String normalizeTypeCode(String value) {
        String input = normalizeInput(value);
        if (input.isEmpty() || !isNumeric(input)) {
            return input;
        }
        long numeric = Long.parseLong(input) % 100L;
        return String.format("%02d", numeric);
    }

    /** COBOL {@code IS NUMERIC} on a PIC X field: every character is a digit. */
    public boolean isNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }
}
