package com.carddemo.batch.exportimport;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;

import java.util.List;

/**
 * Reads the five master files the way CBEXPORT's PROCEDURE DIVISION does: all
 * customers, then accounts, then cross-references, then transactions, then
 * cards (CBEXPORT.cbl:151-157), each in ascending key order because the legacy
 * FDs are INDEXED files with {@code ACCESS MODE IS SEQUENTIAL}.
 *
 * <p>The legacy program's per-group DISPLAY lines are emitted here too: a
 * 'Processing ... records' line when a group starts and a '... exported: n'
 * line when it runs out, so the job log reads exactly like the mainframe one
 * even though all five groups now live in a single step.
 */
public class ExportSourceItemReader implements ItemStreamReader<Object> {

    /** One master file: its export record type and the reader that streams it. */
    public record Group(char recordType, ItemStreamReader<?> reader) {
    }

    private final List<Group> groups;
    private final ExportStatistics statistics;

    private int index;
    private boolean groupStarted;

    public ExportSourceItemReader(List<Group> groups, ExportStatistics statistics) {
        this.groups = List.copyOf(groups);
        this.statistics = statistics;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        groups.forEach(group -> group.reader().open(executionContext));
    }

    @Override
    public Object read() throws Exception {
        while (index < groups.size()) {
            Group group = groups.get(index);
            if (!groupStarted) {
                statistics.startGroup(group.recordType());
                groupStarted = true;
            }
            Object item = group.reader().read();
            if (item != null) {
                statistics.recordExported(group.recordType());
                return item;
            }
            statistics.endGroup(group.recordType());
            index++;
            groupStarted = false;
        }
        return null;
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        groups.forEach(group -> group.reader().update(executionContext));
    }

    @Override
    public void close() throws ItemStreamException {
        groups.forEach(group -> group.reader().close());
    }
}
