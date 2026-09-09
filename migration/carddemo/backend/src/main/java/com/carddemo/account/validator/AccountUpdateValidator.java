package com.carddemo.account.validator;

import com.carddemo.account.dto.AccountFields;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 1200-EDIT-MAP-INPUTS (COACTUPC:1429-1680): every field is edited, in this exact order, but only
 * the first message survives ({@code IF WS-RETURN-MSG-OFF}). Returns that message, or
 * {@code null} when the screen is clean.
 */
@Component
public class AccountUpdateValidator {

    private final AccountDateValidator dateValidator;
    private final Clock clock;

    @Autowired
    public AccountUpdateValidator(AccountDateValidator dateValidator) {
        this(dateValidator, Clock.systemDefaultZone());
    }

    /** Lets the tests pin "today" for the date-of-birth reasonableness check. */
    public AccountUpdateValidator(AccountDateValidator dateValidator, Clock clock) {
        this.dateValidator = dateValidator;
        this.clock = clock;
    }

    public String validate(AccountFields fields) {
        String message = AccountFieldEdits.yesNo("Account Status", fields.activeStatus);

        message = first(message, dateValidator.validate("Open Date",
                fields.openYear, fields.openMonth, fields.openDay));
        message = first(message, AccountFieldEdits.signedAmount("Credit Limit", fields.creditLimit));
        message = first(message, dateValidator.validate("Expiry Date",
                fields.expiryYear, fields.expiryMonth, fields.expiryDay));
        message = first(message,
                AccountFieldEdits.signedAmount("Cash Credit Limit", fields.cashCreditLimit));
        message = first(message, dateValidator.validate("Reissue Date",
                fields.reissueYear, fields.reissueMonth, fields.reissueDay));
        message = first(message, AccountFieldEdits.signedAmount("Current Balance", fields.currBal));
        message = first(message,
                AccountFieldEdits.signedAmount("Current Cycle Credit Limit", fields.currCycCredit));
        message = first(message,
                AccountFieldEdits.signedAmount("Current Cycle Debit Limit", fields.currCycDebit));

        message = first(message, editSsn(fields));
        message = first(message, editDateOfBirth(fields));
        message = first(message, editFicoScore(fields));

        message = first(message, AccountFieldEdits.alphaRequired("First Name", fields.firstName, 25));
        message = first(message, AccountFieldEdits.alphaOptional("Middle Name", fields.middleName, 25));
        message = first(message, AccountFieldEdits.alphaRequired("Last Name", fields.lastName, 25));
        message = first(message, AccountFieldEdits.mandatory("Address Line 1", fields.addrLine1, 50));

        String stateError = AccountFieldEdits.alphaRequired("State", fields.state, 2);
        if (stateError == null && !UsLookupTables.isStateCode(AccountFieldEdits.field(fields.state, 2))) {
            stateError = "State: is not a valid state code";
        }
        message = first(message, stateError);

        String zipError = AccountFieldEdits.numericRequired("Zip", fields.zip, 5);
        message = first(message, zipError);

        message = first(message, AccountFieldEdits.alphaRequired("City", fields.city, 50));
        message = first(message, AccountFieldEdits.alphaRequired("Country", fields.country, 3));
        message = first(message, editPhone("Phone Number 1",
                fields.phone1Area, fields.phone1Prefix, fields.phone1Line));
        message = first(message, editPhone("Phone Number 2",
                fields.phone2Area, fields.phone2Prefix, fields.phone2Line));
        message = first(message,
                AccountFieldEdits.numericRequired("EFT Account Id", fields.eftAccountId, 10));
        message = first(message,
                AccountFieldEdits.yesNo("Primary Card Holder", fields.priCardHolderInd));

        if (stateError == null && zipError == null) {
            String combination = AccountFieldEdits.field(fields.state, 2)
                    + AccountFieldEdits.field(fields.zip, 5).substring(0, 2);
            if (!UsLookupTables.isStateZipCombination(combination)) {
                message = first(message, "Invalid zip code for state");
            }
        }
        return message;
    }

    /** 1265-EDIT-US-SSN (COACTUPC:2431-2492). */
    private String editSsn(AccountFields fields) {
        String part1Error = AccountFieldEdits.numericRequired("SSN: First 3 chars", fields.ssnPart1, 3);
        if (part1Error == null) {
            int part1 = Integer.parseInt(AccountFieldEdits.field(fields.ssnPart1, 3));
            if (part1 == 0 || part1 == 666 || (part1 >= 900 && part1 <= 999)) {
                part1Error = "SSN: First 3 chars: should not be 000, 666, or between 900 and 999";
            }
        }
        String message = part1Error;
        message = first(message,
                AccountFieldEdits.numericRequired("SSN 4th & 5th chars", fields.ssnPart2, 2));
        message = first(message,
                AccountFieldEdits.numericRequired("SSN Last 4 chars", fields.ssnPart3, 4));
        return message;
    }

    /** COACTUPC:1533-1543 — the reasonableness check only runs on an otherwise valid date. */
    private String editDateOfBirth(AccountFields fields) {
        String error = dateValidator.validate("Date of Birth",
                fields.dobYear, fields.dobMonth, fields.dobDay);
        if (error != null) {
            return error;
        }
        return dateValidator.notInFuture("Date of Birth", fields.dobYear, fields.dobMonth,
                fields.dobDay, LocalDate.now(clock));
    }

    /** COACTUPC:1545-1556 with 1275-EDIT-FICO-SCORE. */
    private String editFicoScore(AccountFields fields) {
        String error = AccountFieldEdits.numericRequired("FICO Score", fields.ficoScore, 3);
        if (error != null) {
            return error;
        }
        int fico = Integer.parseInt(AccountFieldEdits.field(fields.ficoScore, 3));
        return fico >= 300 && fico <= 850 ? null : "FICO Score: should be between 300 and 850";
    }

    /**
     * 1260-EDIT-US-PHONE-NUM (COACTUPC:2225-2426). A phone number is optional as a whole; note the
     * copy/paste in the "all parts blank" test (quirk FR-AQ-09).
     */
    private String editPhone(String name, String area, String prefix, String line) {
        boolean areaBlank = AccountFieldEdits.notSupplied(area);
        boolean prefixBlank = AccountFieldEdits.notSupplied(prefix);
        boolean lineBlank = AccountFieldEdits.notSupplied(line);
        if (areaBlank && prefixBlank && (areaBlank || lineBlank)) {
            return null;
        }
        String message = editPhonePart(name + ": Area code", area, 3);
        if (message == null && !UsLookupTables.isGeneralPurposeAreaCode(AccountFieldEdits.field(area, 3))) {
            message = name + ": Not valid North America general purpose area code";
        }
        message = first(message, editPhonePart(name + ": Prefix code", prefix, 3));
        message = first(message, editPhonePart(name + ": Line number code", line, 4));
        return message;
    }

    private String editPhonePart(String qualifiedName, String value, int length) {
        String edited = AccountFieldEdits.field(value, length);
        if (AccountFieldEdits.notSupplied(edited)) {
            return qualifiedName + " must be supplied.";
        }
        if (!edited.matches("[0-9]{" + length + "}")) {
            return qualifiedName + " must be A " + length + " digit number.";
        }
        return Integer.parseInt(edited) == 0 ? qualifiedName + " cannot be zero" : null;
    }

    private String first(String current, String candidate) {
        return current != null ? current : candidate;
    }
}
