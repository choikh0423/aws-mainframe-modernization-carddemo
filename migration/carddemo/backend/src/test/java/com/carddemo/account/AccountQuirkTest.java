package com.carddemo.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.account.dto.AccountFields;
import com.carddemo.account.exception.AccountFilterException;
import com.carddemo.account.util.AccountMessages;
import com.carddemo.account.validator.AccountChangeDetector;
import com.carddemo.account.validator.AccountDateValidator;
import com.carddemo.account.validator.AccountFieldEdits;
import com.carddemo.account.validator.AccountFilterValidator;
import com.carddemo.account.validator.AccountUpdateValidator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * The legacy quirks FR-AQ-01..FR-AQ-09: each one is preserved on purpose, so each one is
 * pinned by an assertion here rather than being "fixed" in the production code.
 */
class AccountQuirkTest {

    private final AccountFilterValidator filterValidator = new AccountFilterValidator();
    private final AccountUpdateValidator updateValidator = new AccountUpdateValidator(
            new AccountDateValidator(),
            Clock.fixed(LocalDate.of(2024, 5, 20).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));

    /** FR-AQ-01: the blank branch's "Account number not provided" never reaches the screen. */
    @Test
    void aBlankFilterAlwaysReportsNoInputReceived() {
        assertThatThrownBy(() -> filterValidator.validateViewFilter("           "))
                .isInstanceOf(AccountFilterException.class)
                .hasMessage("No input received");
        assertThatThrownBy(() -> filterValidator.validateUpdateFilter(""))
                .isInstanceOf(AccountFilterException.class)
                .hasMessage("No input received");
    }

    /** FR-AQ-02, FR-AQ-03: the friendly 88-level texts exist but the runtime text carries codes. */
    @Test
    void theNotFoundTextIsTheDiagnosticOneNotTheFriendly88Level() {
        assertThat(AccountMessages.xrefNotFound("00000000001", 13, 0))
                .startsWith("Account:00000000001 not found in Cross ref file.")
                // The declared-but-never-set 88 level text is not carried over as dead code.
                .isNotEqualTo("Did not find this account in account card xref file");
    }

    /** FR-AQ-04: the two screens word the same condition differently. */
    @Test
    void theTwoScreensDisagreeOnTheInvalidAccountIdText() {
        assertThatThrownBy(() -> filterValidator.validateViewFilter("1234"))
                .hasMessage("Account Filter must  be a non-zero 11 digit number");
        assertThatThrownBy(() -> filterValidator.validateUpdateFilter("1234"))
                .hasMessage("Account Number if supplied must be a 11 digit Non-Zero Number");
    }

    /** FR-AQ-06: a typed '*' means "not supplied". */
    @Test
    void anAsteriskCountsAsNotSupplied() {
        assertThat(AccountFieldEdits.notSupplied("*")).isTrue();
        AccountFields fields = AccountFieldsFixture.validScreen();
        fields.lastName = "*";
        assertThat(updateValidator.validate(fields)).isEqualTo("Last Name must be supplied.");
    }

    /** FR-AQ-07: zip is edited as a number, so 00000 fails as a zero rather than a format. */
    @Test
    void anAllZeroZipIsRejectedAsZero() {
        AccountFields fields = AccountFieldsFixture.validScreen();
        fields.zip = "00000";
        assertThat(updateValidator.validate(fields)).isEqualTo("Zip must not be zero.");
    }

    /**
     * FR-AQ-09: the "no phone supplied" test looks at the area code twice, so a blank area code
     * and prefix make the whole number optional even when a line number was typed.
     */
    @Test
    void aBlankAreaCodeAndPrefixSkipTheLineNumberEdits() {
        AccountFields fields = AccountFieldsFixture.validScreen();
        fields.phone2Area = "";
        fields.phone2Prefix = "";
        fields.phone2Line = "8684";
        assertThat(updateValidator.validate(fields)).isNull();
    }

    /** FR-AQ-05, FR-AU-07: the stale check compares the same date through both slicings. */
    @Test
    void theStaleCheckComparesDatesThroughTheirParts() {
        AccountChangeDetector detector = new AccountChangeDetector();
        AccountFields stored = AccountFieldsFixture.validScreen();
        AccountFields snapshot = AccountFieldsFixture.copyOf(stored);
        assertThat(detector.hasChanges(snapshot, stored)).isFalse();
        snapshot.dobMonth = "07";
        assertThat(detector.hasChanges(snapshot, stored)).isTrue();
    }
}
