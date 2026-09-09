package com.carddemo.account.validator;

import com.carddemo.account.dto.AccountFields;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 1205-COMPARE-OLD-NEW (COACTUPC:1681-1779) and 9700-CHECK-CHANGE-IN-REC (COACTUPC:4109-4202):
 * amounts compare numerically, the account status / group id / customer text fields compare
 * trimmed and case insensitively, everything else compares character for character.
 */
@Component
public class AccountChangeDetector {

    public boolean hasChanges(AccountFields original, AccountFields updated) {
        return !sameAccount(original, updated) || !sameCustomer(original, updated);
    }

    private boolean sameAccount(AccountFields original, AccountFields updated) {
        return exact(original.accountId, updated.accountId)
                && loose(original.activeStatus, updated.activeStatus)
                && sameAmount(original.currBal, updated.currBal)
                && sameAmount(original.creditLimit, updated.creditLimit)
                && sameAmount(original.cashCreditLimit, updated.cashCreditLimit)
                && sameDate(original.openYear, original.openMonth, original.openDay,
                        updated.openYear, updated.openMonth, updated.openDay)
                && sameDate(original.expiryYear, original.expiryMonth, original.expiryDay,
                        updated.expiryYear, updated.expiryMonth, updated.expiryDay)
                && sameDate(original.reissueYear, original.reissueMonth, original.reissueDay,
                        updated.reissueYear, updated.reissueMonth, updated.reissueDay)
                && sameAmount(original.currCycCredit, updated.currCycCredit)
                && sameAmount(original.currCycDebit, updated.currCycDebit)
                && loose(original.groupId, updated.groupId);
    }

    private boolean sameCustomer(AccountFields original, AccountFields updated) {
        return loose(original.custId, updated.custId)
                && loose(original.firstName, updated.firstName)
                && loose(original.middleName, updated.middleName)
                && loose(original.lastName, updated.lastName)
                && loose(original.addrLine1, updated.addrLine1)
                && loose(original.addrLine2, updated.addrLine2)
                && loose(original.city, updated.city)
                && loose(original.state, updated.state)
                && loose(original.country, updated.country)
                && loose(original.zip, updated.zip)
                && exact(original.phone1Area, updated.phone1Area)
                && exact(original.phone1Prefix, updated.phone1Prefix)
                && exact(original.phone1Line, updated.phone1Line)
                && exact(original.phone2Area, updated.phone2Area)
                && exact(original.phone2Prefix, updated.phone2Prefix)
                && exact(original.phone2Line, updated.phone2Line)
                && exact(original.ssnPart1 + original.ssnPart2 + original.ssnPart3,
                        updated.ssnPart1 + updated.ssnPart2 + updated.ssnPart3)
                && loose(original.govtIssuedId, updated.govtIssuedId)
                && sameDate(original.dobYear, original.dobMonth, original.dobDay,
                        updated.dobYear, updated.dobMonth, updated.dobDay)
                && exact(original.eftAccountId, updated.eftAccountId)
                && loose(original.priCardHolderInd, updated.priCardHolderInd)
                && exact(original.ficoScore, updated.ficoScore);
    }

    private boolean sameAmount(String original, String updated) {
        BigDecimal left = AccountFieldEdits.parseAmount(original);
        BigDecimal right = AccountFieldEdits.parseAmount(updated);
        if (left == null || right == null) {
            return left == null && right == null && exact(original, updated);
        }
        return left.compareTo(right) == 0;
    }

    private boolean sameDate(String year, String month, String day,
                             String otherYear, String otherMonth, String otherDay) {
        return exact(year + month + day, otherYear + otherMonth + otherDay);
    }

    private boolean exact(String original, String updated) {
        return Objects.equals(nullToEmpty(original), nullToEmpty(updated));
    }

    private boolean loose(String original, String updated) {
        return nullToEmpty(original).trim().equalsIgnoreCase(nullToEmpty(updated).trim());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
