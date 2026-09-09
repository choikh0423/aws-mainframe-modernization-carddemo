package com.carddemo.account.validator;

import com.carddemo.account.exception.AccountFilterException;
import com.carddemo.account.util.AccountMessages;
import org.springframework.stereotype.Component;

/**
 * 2210-EDIT-ACCOUNT of COACTVWC (COACTVWC:622-680) and 1210-EDIT-ACCOUNT of COACTUPC
 * (COACTUPC:1783-1818). Same rule, two different messages — see quirk FR-AQ-04.
 */
@Component
public class AccountFilterValidator {

    /** The view screen's edit. */
    public long validateViewFilter(String accountId) {
        return validate(accountId, AccountMessages.VIEW_INVALID_ACCOUNT_FILTER);
    }

    /** The update screen's edit. */
    public long validateUpdateFilter(String accountId) {
        return validate(accountId, AccountMessages.UPDATE_INVALID_ACCOUNT_FILTER);
    }

    private long validate(String accountId, String invalidMessage) {
        String edited = AccountFieldEdits.field(accountId, 11);
        if (AccountFieldEdits.notSupplied(edited)) {
            throw new AccountFilterException(AccountMessages.NO_INPUT_RECEIVED);
        }
        if (!edited.matches("[0-9]{11}")) {
            throw new AccountFilterException(invalidMessage);
        }
        long value = Long.parseLong(edited);
        if (value == 0L) {
            throw new AccountFilterException(invalidMessage);
        }
        return value;
    }
}
