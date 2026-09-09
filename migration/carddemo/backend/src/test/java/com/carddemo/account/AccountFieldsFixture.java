package com.carddemo.account;

import com.carddemo.account.dto.AccountFields;

/** A CACTUPA screen that passes every edit, so a test can spoil exactly one field. */
public final class AccountFieldsFixture {

    private AccountFieldsFixture() {
    }

    public static AccountFields validScreen() {
        AccountFields fields = new AccountFields();
        fields.accountId = "00000000001";
        fields.activeStatus = "Y";
        fields.currBal = "+        194.00";
        fields.creditLimit = "+      2,020.00";
        fields.cashCreditLimit = "+      1,020.00";
        fields.currCycCredit = "+          0.00";
        fields.currCycDebit = "+          0.00";
        fields.openYear = "2014";
        fields.openMonth = "11";
        fields.openDay = "20";
        fields.expiryYear = "2025";
        fields.expiryMonth = "05";
        fields.expiryDay = "20";
        fields.reissueYear = "2025";
        fields.reissueMonth = "05";
        fields.reissueDay = "20";
        fields.groupId = "";

        fields.custId = "000000001";
        fields.ssnPart1 = "020";
        fields.ssnPart2 = "97";
        fields.ssnPart3 = "3888";
        fields.ficoScore = "700";
        fields.dobYear = "1961";
        fields.dobMonth = "06";
        fields.dobDay = "08";
        fields.firstName = "Immanuel";
        fields.middleName = "Madeline";
        fields.lastName = "Kessler";
        fields.addrLine1 = "618 Deshaun Route";
        fields.addrLine2 = "Apt. 802";
        fields.city = "Altenwerthshire";
        fields.state = "NC";
        fields.zip = "27546";
        fields.country = "USA";
        fields.phone1Area = "908";
        fields.phone1Prefix = "119";
        fields.phone1Line = "8310";
        fields.phone2Area = "704";
        fields.phone2Prefix = "693";
        fields.phone2Line = "8684";
        fields.govtIssuedId = "00000000000049368437";
        fields.eftAccountId = "0053581756";
        fields.priCardHolderInd = "Y";
        return fields;
    }

    /** A field for field copy, so a test can compare a snapshot against an edited screen. */
    public static AccountFields copyOf(AccountFields source) {
        AccountFields copy = new AccountFields();
        for (java.lang.reflect.Field field : AccountFields.class.getFields()) {
            try {
                field.set(copy, field.get(source));
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return copy;
    }
}
