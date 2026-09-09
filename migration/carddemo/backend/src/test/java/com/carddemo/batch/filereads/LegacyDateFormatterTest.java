package com.carddemo.batch.filereads;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-A13: the JDK replacement for COBDATFT (boundary B-03) behaves as
 * `app/asm/COBDATFT.asm` does.
 */
class LegacyDateFormatterTest {

    @Test
    void expandsAnUnseparatedDateWhenBothTypesAreOne() {
        LegacyDateFormatter.Result result = LegacyDateFormatter.convert(
                LegacyDateFormatter.TYPE_YYYYMMDD, LegacyDateFormatter.TYPE_YYYYMMDD, "20250520");

        assertThat(result.isError()).isFalse();
        assertThat(result.outDate()).startsWith("2025-05-20");
        assertThat(result.outDate()).hasSize(20);
    }

    @Test
    void compressesASeparatedDateWhenBothTypesAreTwo() {
        LegacyDateFormatter.Result result = LegacyDateFormatter.convert(
                LegacyDateFormatter.TYPE_YYYY_MM_DD, LegacyDateFormatter.TYPE_YYYY_MM_DD, "2025-05-20");

        assertThat(result.isError()).isFalse();
        assertThat(result.outDate()).startsWith("20250520");
    }

    @Test
    void rejectsAnInTypeOneDateThatIsAlreadySeparated() {
        LegacyDateFormatter.Result result = LegacyDateFormatter.convert(
                LegacyDateFormatter.TYPE_YYYYMMDD, LegacyDateFormatter.TYPE_YYYYMMDD, "2025-05-20");

        assertThat(result.errorMessage()).isEqualTo(LegacyDateFormatter.INVALID_INPUT);
        assertThat(result.outDate()).isBlank();
    }

    @Test
    void rejectsCrossedTypePairsAndUnknownTypes() {
        assertThat(LegacyDateFormatter.convert("1", "2", "20250520").errorMessage())
                .isEqualTo(LegacyDateFormatter.INVALID_INPUT);
        assertThat(LegacyDateFormatter.convert("2", "1", "2025-05-20").errorMessage())
                .isEqualTo(LegacyDateFormatter.INVALID_INPUT);
        assertThat(LegacyDateFormatter.convert("3", "3", "20250520").errorMessage())
                .isEqualTo(LegacyDateFormatter.INVALID_INPUT);
    }

    @Test
    void copiesCharactersWithoutValidatingTheDate() {
        // The Assembler never checks the digits, and neither does the replacement.
        assertThat(LegacyDateFormatter.convert("1", "1", "2025AA99").outDate())
                .startsWith("2025-AA-99");
    }
}
