package com.carddemo.batch.exportimport;

import com.carddemo.common.batch.AbendService;
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
 * even though all five groups now live in a single step. A source that cannot
 * be opened or read displays the FD's
 * {@code 'ERROR: Cannot open CUSTOMER-INPUT, Status: '} /
 * {@code 'ERROR: Reading CUSTOMER-INPUT, Status: '} line and abends, as
 * 1100-OPEN-FILES and each 2100-READ paragraph do.
 */
public class ExportSourceItemReader implements ItemStreamReader<Object> {

    /**
     * One master file: its export record type, the FD name CBEXPORT's SYSOUT
     * calls it by, and the reader that streams it.
     */
    public record Group(char recordType, String fdName, ItemStreamReader<?> reader) {
    }

    private final List<Group> groups;
    private final ExportStatistics statistics;
    private final AbendService abendService;

    private int index;
    private boolean groupStarted;

    public ExportSourceItemReader(List<Group> groups, ExportStatistics statistics,
            AbendService abendService) {
        this.groups = List.copyOf(groups);
        this.statistics = statistics;
        this.abendService = abendService;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        for (Group group : groups) {
            try {
                group.reader().open(executionContext);
            } catch (RuntimeException e) {
                throw ExportImportSysout.abend(abendService, "CBEXPORT", e.toString(),
                        ExportImportSysout.cannotOpen(group.fdName(),
                                ExportImportSysout.STATUS_OPEN_FAILED));
            }
        }
    }

    @Override
    public Object read() throws Exception {
        while (index < groups.size()) {
            Group group = groups.get(index);
            if (!groupStarted) {
                statistics.startGroup(group.recordType());
                groupStarted = true;
            }
            Object item;
            try {
                item = group.reader().read();
            } catch (Exception e) {
                throw ExportImportSysout.abend(abendService, "CBEXPORT", e.toString(),
                        ExportImportSysout.reading(group.fdName(),
                                ExportImportSysout.STATUS_READ_FAILED));
            }
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
