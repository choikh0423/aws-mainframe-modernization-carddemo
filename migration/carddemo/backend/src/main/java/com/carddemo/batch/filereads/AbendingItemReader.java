package com.carddemo.batch.filereads;

import com.carddemo.common.batch.AbendException;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;

/**
 * The {@code OPEN INPUT} / {@code READ} / {@code CLOSE} error handling of the S-15
 * programs, wrapped around the reader that supplies the records.
 *
 * <p>The COBOL checked the file status after each of the three operations and, for
 * anything other than {@code 00} (or {@code 10} on a read, which ends the browse),
 * displayed its error literal and abended (FR-A14, FR-C4, FR-X4, FR-U4). Here the
 * equivalent condition is a failure of the underlying query, reported as file status
 * {@code 30}.
 */
public class AbendingItemReader<T> implements ItemStreamReader<T> {

    private final ItemStreamReader<T> delegate;
    private final AbendSink abendSink;
    private final String openError;
    private final String readError;
    private final String closeError;

    public AbendingItemReader(ItemStreamReader<T> delegate, AbendSink abendSink,
                              String openError, String readError, String closeError) {
        this.delegate = delegate;
        this.abendSink = abendSink;
        this.openError = openError;
        this.readError = readError;
        this.closeError = closeError;
    }

    @Override
    public T read() {
        try {
            return delegate.read();
        } catch (AbendException e) {
            throw e;
        } catch (Exception e) {
            throw abendSink.abend(readError, FileReadAbend.PERMANENT_ERROR);
        }
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        try {
            delegate.open(executionContext);
        } catch (AbendException e) {
            throw e;
        } catch (RuntimeException e) {
            throw abendSink.abend(openError, FileReadAbend.PERMANENT_ERROR);
        }
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        delegate.update(executionContext);
    }

    @Override
    public void close() throws ItemStreamException {
        try {
            delegate.close();
        } catch (AbendException e) {
            throw e;
        } catch (RuntimeException e) {
            throw abendSink.abend(closeError, FileReadAbend.PERMANENT_ERROR);
        }
    }
}
