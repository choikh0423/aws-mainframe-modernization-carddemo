package com.carddemo.batch.exportimport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The storage forms of CVEXPORT.cpy. These are the byte-level rules the whole
 * stream rests on, so they are checked against hand-written expected bytes
 * rather than against a round trip through the codec itself.
 */
class MainframeFieldCodecTest {

    private final byte[] record = blank(40);

    @Test
    void textIsLeftJustifiedAndSpacePadded() {
        MainframeFieldCodec.putText(record, 0, 10, "JOHN");

        assertThat(text(0, 10)).isEqualTo("JOHN      ");
    }

    @Test
    void textLongerThanThePictureIsTruncatedOnTheRight() {
        MainframeFieldCodec.putText(record, 0, 4, "JOHNSON");

        assertThat(text(0, 4)).isEqualTo("JOHN");
    }

    @Test
    void nullTextIsAllSpaces() {
        MainframeFieldCodec.putText(record, 0, 5, null);

        assertThat(text(0, 5)).isEqualTo("     ");
    }

    @Test
    void getTextStripsTheTrailingPaddingOnly() {
        MainframeFieldCodec.putText(record, 0, 10, "  JOHN");

        assertThat(MainframeFieldCodec.getText(record, 0, 10)).isEqualTo("  JOHN");
    }

    @Test
    void zonedIsZeroPadded() {
        MainframeFieldCodec.putZoned(record, 0, 9, 12345L);

        assertThat(text(0, 9)).isEqualTo("000012345");
        assertThat(MainframeFieldCodec.getZoned(record, 0, 9)).isEqualTo(12345L);
    }

    @Test
    void aZonedFieldTooSmallForTheValueKeepsTheLowOrderDigitsAsACobolMoveDoes() {
        MainframeFieldCodec.putZoned(record, 0, 7, 123456789L);

        assertThat(text(0, 7)).isEqualTo("3456789");
    }

    @Test
    void anAllBlankZonedFieldReadsAsZero() {
        assertThat(MainframeFieldCodec.getZoned(record, 0, 9)).isZero();
    }

    /** {@code {}=+0 … I=+9}, {@code }=-0 … R=-9} - the sign is on the last digit. */
    @ParameterizedTest
    @CsvSource({
            "13.75, 000000137E",
            "-13.75, 000000137N",
            "0.00, 000000000{",
            "-0.05, 000000000N",
            "9.99, 000000099I"
    })
    void signedZonedCarriesATrailingOverpunch(String value, String expected) {
        MainframeFieldCodec.putZonedSigned(record, 0, expected.length(), new BigDecimal(value), 2);

        assertThat(text(0, expected.length())).isEqualTo(expected);
        assertThat(MainframeFieldCodec.getZonedSigned(record, 0, expected.length(), 2))
                .isEqualByComparingTo(new BigDecimal(value));
    }

    @Test
    void binaryIsBigEndian() {
        MainframeFieldCodec.putBinary(record, 0, 4, 305419896L);

        assertThat(Arrays.copyOfRange(record, 0, 4))
                .containsExactly((byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78);
        assertThat(MainframeFieldCodec.getBinary(record, 0, 4)).isEqualTo(305419896L);
    }

    @Test
    void binaryDecimalStoresTheUnscaledValue() {
        MainframeFieldCodec.putBinaryDecimal(record, 0, 8, new BigDecimal("13.75"), 2);

        assertThat(MainframeFieldCodec.getBinary(record, 0, 8)).isEqualTo(1375L);
        assertThat(MainframeFieldCodec.getBinaryDecimal(record, 0, 8, 2))
                .isEqualByComparingTo(new BigDecimal("13.75"));
    }

    @Test
    void packedHoldsTwoDigitsPerByteWithASignNibble() {
        MainframeFieldCodec.putPacked(record, 0, 7, new BigDecimal("13.75"), 2, true);

        assertThat(Arrays.copyOfRange(record, 0, 7)).containsExactly(
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x01, (byte) 0x37, (byte) 0x5C);
        assertThat(MainframeFieldCodec.getPacked(record, 0, 7, 2))
                .isEqualByComparingTo(new BigDecimal("13.75"));
    }

    @Test
    void packedUsesTheDSignNibbleWhenNegative() {
        MainframeFieldCodec.putPacked(record, 0, 7, new BigDecimal("-13.75"), 2, true);

        assertThat(record[6]).isEqualTo((byte) 0x5D);
        assertThat(MainframeFieldCodec.getPacked(record, 0, 7, 2))
                .isEqualByComparingTo(new BigDecimal("-13.75"));
    }

    @Test
    void anUnsignedPackedFieldUsesTheFSignNibble() {
        MainframeFieldCodec.putPacked(record, 0, 2, BigDecimal.valueOf(789), 0, false);

        assertThat(Arrays.copyOfRange(record, 0, 2)).containsExactly((byte) 0x78, (byte) 0x9F);
        assertThat(MainframeFieldCodec.getPacked(record, 0, 2, 0))
                .isEqualByComparingTo(BigDecimal.valueOf(789));
    }

    @Test
    void aNullAmountIsStoredAsZero() {
        MainframeFieldCodec.putPacked(record, 0, 7, null, 2, true);
        MainframeFieldCodec.putZonedSigned(record, 10, 12, null, 2);

        assertThat(MainframeFieldCodec.getPacked(record, 0, 7, 2)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(MainframeFieldCodec.getZonedSigned(record, 10, 12, 2)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private String text(int offset, int length) {
        return new String(record, offset, length, StandardCharsets.ISO_8859_1);
    }

    private static byte[] blank(int length) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) ' ');
        return bytes;
    }
}
