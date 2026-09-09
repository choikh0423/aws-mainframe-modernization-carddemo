package com.carddemo.account.validator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** FR-AU-24, FR-AU-25, FR-AU-26: the CSLKPCDY tables, transcribed value for value. */
class UsLookupTablesTest {

    @Test
    void areaCodesFollowTheCopybook() {
        assertThat(UsLookupTables.isGeneralPurposeAreaCode("908")).isTrue();
        assertThat(UsLookupTables.isGeneralPurposeAreaCode("201")).isTrue();
        assertThat(UsLookupTables.isGeneralPurposeAreaCode("555")).isFalse();
        assertThat(UsLookupTables.isGeneralPurposeAreaCode("000")).isFalse();
    }

    @Test
    void stateCodesIncludeTerritories() {
        assertThat(UsLookupTables.isStateCode("NC")).isTrue();
        assertThat(UsLookupTables.isStateCode("PR")).isTrue();
        assertThat(UsLookupTables.isStateCode("XX")).isFalse();
    }

    @Test
    void stateZipCombinationsAreStatePlusFirstTwoZipDigits() {
        assertThat(UsLookupTables.isStateZipCombination("NC27")).isTrue();
        assertThat(UsLookupTables.isStateZipCombination("NC99")).isFalse();
    }
}
