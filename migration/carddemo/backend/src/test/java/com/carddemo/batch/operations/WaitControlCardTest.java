package com.carddemo.batch.operations;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The reading rules of the SYSIN card COBSWAIT accepts (COBSWAIT.cbl:36-37).
 *
 * <p>Covers FR-OC-02, FR-OC-03, FR-OC-05, FR-OC-06, FR-OC-08, FR-OC-09, FR-OC-11.
 */
class WaitControlCardTest {

    @Test
    void readsTheEightValueColumnsAsCentiseconds() {
        WaitControlCard card = WaitControlCard.parse("00003600      VALUE IN CENTISECONDS");

        assertThat(card.value()).isEqualTo("00003600");
        assertThat(card.centiseconds()).isEqualTo(3600L);
        assertThat(card.millis()).isEqualTo(36_000L);
    }

    @Test
    void ignoresEverythingAfterColumnEight() {
        assertThat(WaitControlCard.parse("00000100 IGNORED").centiseconds()).isEqualTo(100L);
    }

    @Test
    void readsOnlyTheFirstRecord() {
        assertThat(WaitControlCard.parse("00000005\n00009999").centiseconds()).isEqualTo(5L);
    }

    @Test
    void zeroWaitsNotAtAll() {
        assertThat(WaitControlCard.parse("00000000").millis()).isZero();
    }

    @Test
    void acceptsTheLargestValueMvswaitTimeCanHold() {
        WaitControlCard card = WaitControlCard.parse("99999999");

        assertThat(card.centiseconds()).isEqualTo(WaitControlCard.MAX_CENTISECONDS);
    }

    @Test
    void rejectsAValueThatIsNotEightDigits() {
        assertThatThrownBy(() -> WaitControlCard.parse("    3600"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not 8 numeric digits");
    }

    @Test
    void rejectsAShortCard() {
        assertThatThrownBy(() -> WaitControlCard.parse("3600"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnEmptySysin() {
        assertThatThrownBy(() -> WaitControlCard.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
