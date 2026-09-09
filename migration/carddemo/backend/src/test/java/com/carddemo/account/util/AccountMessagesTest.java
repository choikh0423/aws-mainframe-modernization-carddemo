package com.carddemo.account.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** FR-AV-05..07: the runtime diagnostics CAVW builds instead of its declared friendly literals. */
class AccountMessagesTest {

    /** The built text is 79 characters, so WS-RETURN-MSG PIC X(75) clips the reason code. */
    @Test
    void xrefNotFoundCarriesTheAccountIdAndIsClippedAtSeventyFive() {
        assertThat(AccountMessages.xrefNotFound("00000000001", 13, 0))
                .isEqualTo("Account:00000000001 not found in Cross ref file.  Resp:000000013  Reas:0000");
    }

    @Test
    void accountNotFoundCarriesTheAccountIdAndIsClippedAtSeventyFive() {
        assertThat(AccountMessages.accountNotFound("00000000001", 13, 0))
                .isEqualTo("Account:00000000001 not found in Acct Master file.Resp:000000013  Reas:0000");
    }

    @Test
    void customerNotFoundIsTruncatedToTheSeventyFiveCharacterReturnMessage() {
        String message = AccountMessages.customerNotFound("000000001", 13, 0);
        assertThat(message).hasSizeLessThanOrEqualTo(75);
        assertThat(message).startsWith("CustId:000000001 not found in customer master.Resp: 000000013");
    }

    @Test
    void literalsKeepTheirLegacyTypography() {
        assertThat(AccountMessages.VIEW_INVALID_ACCOUNT_FILTER)
                .isEqualTo("Account Filter must  be a non-zero 11 digit number");
        assertThat(AccountMessages.UPDATE_CHANGES_VALIDATED).isEqualTo("Changes validated.Press F5 to save");
        assertThat(AccountMessages.NO_CHANGE_DETECTED)
                .isEqualTo("No change detected with respect to values fetched.");
    }
}
