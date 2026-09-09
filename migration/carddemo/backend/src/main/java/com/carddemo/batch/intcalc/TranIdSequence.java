package com.carddemo.batch.intcalc;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;

/**
 * {@code WS-TRANID-SUFFIX} of CBACT04C (CBACT04C.cbl:173,474-480): a run-wide
 * counter incremented before every generated transaction, concatenated onto the
 * 10-character PARM date to form {@code TRAN-ID}.
 *
 * <pre>
 * STRING PARM-DATE, WS-TRANID-SUFFIX DELIMITED BY SIZE INTO TRAN-ID
 * </pre>
 *
 * <p>The counter is kept in the step's ExecutionContext so a restart continues
 * numbering where the last committed chunk stopped instead of reissuing ids.
 */
class TranIdSequence implements ItemStream {

    private static final String CONTEXT_KEY = "intcalc.tranid.suffix";

    private final String runDate;

    private long suffix;

    TranIdSequence(String runDate) {
        this.runDate = runDate;
    }

    /** ADD 1 TO WS-TRANID-SUFFIX, then PARM-DATE + the 6-digit suffix. */
    String nextTranId() {
        suffix++;
        return runDate + String.format("%06d", suffix);
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        suffix = executionContext.getLong(CONTEXT_KEY, 0L);
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        executionContext.putLong(CONTEXT_KEY, suffix);
    }

    @Override
    public void close() throws ItemStreamException {
        // nothing to release
    }
}
