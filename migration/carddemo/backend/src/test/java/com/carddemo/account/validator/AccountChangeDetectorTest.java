package com.carddemo.account.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.account.AccountFieldsFixture;
import com.carddemo.account.dto.AccountFields;
import org.junit.jupiter.api.Test;

/** FR-AU-07, FR-AU-11: 1205-COMPARE-OLD-NEW. */
class AccountChangeDetectorTest {

    private final AccountChangeDetector detector = new AccountChangeDetector();

    @Test
    void anUntouchedScreenIsNoChange() {
        AccountFields original = AccountFieldsFixture.validScreen();
        assertThat(detector.hasChanges(original, AccountFieldsFixture.copyOf(original))).isFalse();
    }

    @Test
    void amountsCompareNumericallySoReEditedTextIsNotAChange() {
        AccountFields original = AccountFieldsFixture.validScreen();
        AccountFields updated = AccountFieldsFixture.copyOf(original);
        updated.currBal = "194.00";
        assertThat(detector.hasChanges(original, updated)).isFalse();
        updated.currBal = "194.01";
        assertThat(detector.hasChanges(original, updated)).isTrue();
    }

    @Test
    void textFieldsCompareTrimmedAndCaseInsensitively() {
        AccountFields original = AccountFieldsFixture.validScreen();
        AccountFields updated = AccountFieldsFixture.copyOf(original);
        updated.firstName = "  immanuel ";
        updated.activeStatus = "y";
        assertThat(detector.hasChanges(original, updated)).isFalse();
        updated.lastName = "Kesler";
        assertThat(detector.hasChanges(original, updated)).isTrue();
    }

    @Test
    void phoneSsnDobEftAndFicoCompareCharacterForCharacter() {
        AccountFields original = AccountFieldsFixture.validScreen();

        AccountFields phoneChanged = AccountFieldsFixture.copyOf(original);
        phoneChanged.phone1Line = "8311";
        assertThat(detector.hasChanges(original, phoneChanged)).isTrue();

        AccountFields ssnChanged = AccountFieldsFixture.copyOf(original);
        ssnChanged.ssnPart3 = "3889";
        assertThat(detector.hasChanges(original, ssnChanged)).isTrue();

        AccountFields dobChanged = AccountFieldsFixture.copyOf(original);
        dobChanged.dobDay = "09";
        assertThat(detector.hasChanges(original, dobChanged)).isTrue();

        AccountFields ficoChanged = AccountFieldsFixture.copyOf(original);
        ficoChanged.ficoScore = "701";
        assertThat(detector.hasChanges(original, ficoChanged)).isTrue();
    }

    @Test
    void datesCompareAsTheirConcatenatedParts() {
        AccountFields original = AccountFieldsFixture.validScreen();
        AccountFields updated = AccountFieldsFixture.copyOf(original);
        updated.openMonth = "12";
        assertThat(detector.hasChanges(original, updated)).isTrue();
    }
}
