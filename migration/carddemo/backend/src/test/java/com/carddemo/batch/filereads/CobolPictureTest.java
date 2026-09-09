package com.carddemo.batch.filereads;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-G9, FR-A7: the PIC renderings the S-15 record images and the CBACT01C output
 * datasets are built from.
 */
class CobolPictureTest {

    @Test
    void padsAndTruncatesCharacterFieldsOnTheRight() {
        assertThat(CobolPicture.text("AB", 5)).isEqualTo("AB   ");
        assertThat(CobolPicture.text(null, 3)).isEqualTo("   ");
        assertThat(CobolPicture.text("ABCDE", 3)).isEqualTo("ABC");
    }

    @Test
    void zeroPadsUnsignedNumerics() {
        assertThat(CobolPicture.unsigned(1L, 11)).isEqualTo("00000000001");
        assertThat(CobolPicture.unsigned(null, 3)).isEqualTo("000");
        assertThat(CobolPicture.unsigned(123, 3)).isEqualTo("123");
    }

    @Test
    void overpunchesTheSignOnTheLastDigitOfSignedDisplayNumerics() {
        // +194.00 is the ACCT-CURR-BAL of account 1 in app/data/ASCII/acctdata.txt.
        assertThat(CobolPicture.signed(new BigDecimal("194.00"), 10, 2))
                .isEqualTo("00000001940{");
        assertThat(CobolPicture.signed(new BigDecimal("0.00"), 10, 2))
                .isEqualTo("00000000000{");
        assertThat(CobolPicture.signed(new BigDecimal("-1025.00"), 10, 2))
                .isEqualTo("00000010250}");
    }

    @Test
    void overpunchesEveryDigitValue() {
        String positive = "{ABCDEFGHI";
        String negative = "}JKLMNOPQR";
        for (int digit = 0; digit <= 9; digit++) {
            assertThat(CobolPicture.signed(new BigDecimal("0.0" + digit), 10, 2))
                    .endsWith(String.valueOf(positive.charAt(digit)));
            if (digit > 0) {
                assertThat(CobolPicture.signed(new BigDecimal("-0.0" + digit), 10, 2))
                        .endsWith(String.valueOf(negative.charAt(digit)));
            }
        }
    }

    @Test
    void scalesTheValueToTheDeclaredNumberOfDecimals() {
        assertThat(CobolPicture.signed(new BigDecimal("13.7"), 8, 2)).isEqualTo("000000137{");
    }

    @Test
    void truncatesHighOrderDigitsLikeACobolMove() {
        assertThat(CobolPicture.unsigned(1234L, 3)).isEqualTo("234");
    }

    @Test
    void encodesPackedDecimalWithTheCobolSignNibble() {
        // S9(9)V99 COMP-3 occupies 6 bytes: 11 digits plus the sign nibble.
        assertThat(CobolPicture.packed(new BigDecimal("2525.00"), 9, 2))
                .containsExactly(0x00, 0x00, 0x02, 0x52, 0x50, 0x0C);
        assertThat(CobolPicture.packed(new BigDecimal("-2525.00"), 9, 2))
                .containsExactly(0x00, 0x00, 0x02, 0x52, 0x50, 0x0D);
        assertThat(CobolPicture.packed(BigDecimal.ZERO, 9, 2))
                .containsExactly(0x00, 0x00, 0x00, 0x00, 0x00, 0x0C);
    }
}
