package com.carddemo.batch.authpurge;

import com.carddemo.common.domain.PendingAuthSummaryRecord;
import com.carddemo.pendingauth.repository.PendingAuthSummaryBrowseRepository;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 2000-FIND-NEXT-AUTH-SUMMARY: {@code GN PAUTSUM0} until {@code GB}.
 *
 * <p>Keyset paging rather than offset paging, because the purge deletes roots
 * while the browse runs — the position is the last account id returned, exactly
 * like the IMS position the {@code GN} advances. That position is saved in the
 * {@link ExecutionContext}, so a restart resumes where the last committed chunk
 * ended instead of rescanning the database (D-3).
 */
public class PendingAuthSummaryReader implements ItemStreamReader<PendingAuthSummaryRecord> {

    private static final String POSITION_KEY = "pendingAuth.purge.lastAcctId";
    private static final int FETCH_SIZE = 100;

    private final PendingAuthSummaryBrowseRepository repository;
    private final Deque<PendingAuthSummaryRecord> buffer = new ArrayDeque<>();

    private long position = -1L;

    public PendingAuthSummaryReader(PendingAuthSummaryBrowseRepository repository) {
        this.repository = repository;
    }

    @Override
    public void open(ExecutionContext executionContext) {
        position = executionContext.containsKey(POSITION_KEY)
                ? executionContext.getLong(POSITION_KEY) : -1L;
        buffer.clear();
    }

    @Override
    public void update(ExecutionContext executionContext) {
        executionContext.putLong(POSITION_KEY, position);
    }

    @Override
    public void close() {
        buffer.clear();
    }

    @Override
    public PendingAuthSummaryRecord read() {
        if (buffer.isEmpty()) {
            List<PendingAuthSummaryRecord> page =
                    repository.browseAfter(position, PageRequest.of(0, FETCH_SIZE));
            buffer.addAll(page);
        }
        PendingAuthSummaryRecord next = buffer.poll();
        if (next == null) {
            return null;
        }
        position = next.getPaAcctId();
        return next;
    }
}
