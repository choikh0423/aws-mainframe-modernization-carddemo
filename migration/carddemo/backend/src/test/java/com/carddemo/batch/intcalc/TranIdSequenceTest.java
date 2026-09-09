package com.carddemo.batch.intcalc;

import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-I2, FR-I13: TRAN-ID = PARM-DATE + a run-wide 6-digit suffix. */
class TranIdSequenceTest {

    @Test
    void usesTheRunDateVerbatim() {
        TranIdSequence sequence = opened(new TranIdSequence("2022071800"));

        assertThat(sequence.nextTranId()).isEqualTo("2022071800000001");
    }

    /** The suffix is not reset per account: CBACT04C increments it per written record. */
    @Test
    void numbersTransactionsDenselyAcrossAccounts() {
        TranIdSequence sequence = opened(new TranIdSequence("2022071800"));

        assertThat(sequence.nextTranId()).isEqualTo("2022071800000001");
        assertThat(sequence.nextTranId()).isEqualTo("2022071800000002");
        assertThat(sequence.nextTranId()).isEqualTo("2022071800000003");
    }

    /** A restart continues numbering from the last committed chunk. */
    @Test
    void resumesFromTheExecutionContextOnRestart() {
        ExecutionContext context = new ExecutionContext();
        TranIdSequence first = opened(new TranIdSequence("2022071800"));
        first.nextTranId();
        first.nextTranId();
        first.update(context);

        TranIdSequence restarted = new TranIdSequence("2022071800");
        restarted.open(context);

        assertThat(restarted.nextTranId()).isEqualTo("2022071800000003");
    }

    private static TranIdSequence opened(TranIdSequence sequence) {
        sequence.open(new ExecutionContext());
        return sequence;
    }
}
