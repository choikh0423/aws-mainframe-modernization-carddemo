package com.carddemo.reporting.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-R18 / FR-R25 — {@code COMPUTE <PIC 9(n)> = FUNCTION NUMVAL-C(field)} as
 * CORPT00C applies it to every typed date component (cbl:305-327).
 */
class CobolNumvalTest {

    @ParameterizedTest(name = "NUMVAL-C(\"{0}\") into PIC 99 -> {1}")
    @CsvSource({
            "'1',   01",
            "'1 ',  01",
            "' 7',  07",
            "'07',  07",
            "'12',  12",
            "'31',  31",
            "'99',  99",
    })
    @DisplayName("a typed month or day is echoed back zero-padded to two digits")
    void normalisesTwoDigitFields(String typed, String expected) {
        assertThat(CobolNumval.numvalCInto(typed, 2)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "NUMVAL-C(\"{0}\") into PIC 9999 -> {1}")
    @CsvSource({
            "'2023', 2023",
            "'20',   0020",
            "'  1',  0001",
    })
    @DisplayName("a typed year is echoed back zero-padded to four digits")
    void normalisesTheYear(String typed, String expected) {
        assertThat(CobolNumval.numvalCInto(typed, 4)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "NUMVAL-C(\"{0}\") -> 00")
    @CsvSource({"ab", "'??'", "'--'", "'  '", "''"})
    @DisplayName("FR-R25: text that holds no number yields zero, never an error")
    void nonNumericTextYieldsZero(String typed) {
        assertThat(CobolNumval.numvalCInto(typed, 2)).isEqualTo("00");
    }

    @Test
    @DisplayName("FR-R25: nothing typed at all is zero as well")
    void nullYieldsZero() {
        assertThat(CobolNumval.numvalCInto(null, 4)).isEqualTo("0000");
    }

    @Test
    @DisplayName("the receiving field is unsigned, so a signed value loses its sign")
    void signIsDroppedByTheUnsignedField() {
        assertThat(CobolNumval.numvalCInto("-5", 2)).isEqualTo("05");
    }

    @Test
    @DisplayName("high-order digits are truncated, as a COMPUTE without ON SIZE ERROR does")
    void truncatesHighOrderDigits() {
        assertThat(CobolNumval.numvalCInto("123", 2)).isEqualTo("23");
        assertThat(CobolNumval.numvalCInto("12345", 4)).isEqualTo("2345");
    }

    @Test
    @DisplayName("NUMVAL-C ignores the currency sign and digit separators")
    void ignoresCurrencyAndSeparators() {
        assertThat(CobolNumval.numvalCInto("$1,2", 2)).isEqualTo("12");
    }
}
