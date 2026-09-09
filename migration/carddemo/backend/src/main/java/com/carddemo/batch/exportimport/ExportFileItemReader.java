package com.carddemo.batch.exportimport;

import com.carddemo.common.batch.AbendService;
import org.springframework.batch.item.support.AbstractItemCountingItemStreamItemReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Reads EXPFILE as fixed 500-byte records - the READ of CBIMPORT's
 * 2100-READ-EXPORT-RECORD (CBIMPORT.cbl:259-269). The record is binary, so
 * neither {@code FlatFileItemReader} nor a line-oriented layout applies.
 *
 * <p>Restart support comes from the counting base class: it replays the item
 * count from the ExecutionContext, and this reader seeks straight to that
 * record instead of re-reading the ones before it.
 *
 * <p>A short trailing record cannot happen on a RECFM=F dataset; if the file is
 * truncated the read fails the way the legacy program's non-zero file status
 * did - message then abend.
 *
 * <p>WS-TOTAL-RECORDS-READ is counted here, so it counts unknown record types
 * too (CBIMPORT.cbl:253, FR-I-03).
 */
public class ExportFileItemReader extends AbstractItemCountingItemStreamItemReader<byte[]> {

    static final String STATUS_OPEN_FAILED = ExportImportSysout.STATUS_OPEN_FAILED;
    static final String STATUS_READ_FAILED = ExportImportSysout.STATUS_READ_FAILED;

    private final Path file;
    private final AbendService abendService;
    private final ImportStatistics statistics;

    private FileChannel channel;

    public ExportFileItemReader(Path file, AbendService abendService, ImportStatistics statistics) {
        this.file = file;
        this.abendService = abendService;
        this.statistics = statistics;
        setName("CBIMPORT-EXPFILE");
    }

    @Override
    protected void doOpen() {
        try {
            channel = FileChannel.open(file, StandardOpenOption.READ);
        } catch (IOException e) {
            throw ExportImportSysout.abend(abendService, "CBIMPORT", e.toString(),
                    ExportImportSysout.cannotOpen("EXPORT-INPUT", STATUS_OPEN_FAILED));
        }
    }

    @Override
    protected byte[] doRead() {
        ByteBuffer buffer = ByteBuffer.allocate(ExportRecordCodec.RECORD_LENGTH);
        try {
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) {
                    if (buffer.position() == 0) {
                        return null;
                    }
                    throw ExportImportSysout.abend(abendService, "CBIMPORT",
                            "EXPFILE ends with a partial " + buffer.position() + " byte record",
                            ExportImportSysout.reading("EXPORT-INPUT", STATUS_READ_FAILED));
                }
            }
        } catch (IOException e) {
            throw ExportImportSysout.abend(abendService, "CBIMPORT", e.toString(),
                    ExportImportSysout.reading("EXPORT-INPUT", STATUS_READ_FAILED));
        }
        statistics.recordRead();
        return buffer.array();
    }

    @Override
    protected void jumpToItem(int itemIndex) {
        try {
            channel.position((long) itemIndex * ExportRecordCodec.RECORD_LENGTH);
        } catch (IOException e) {
            throw ExportImportSysout.abend(abendService, "CBIMPORT", e.toString(),
                    ExportImportSysout.reading("EXPORT-INPUT", STATUS_READ_FAILED));
        }
    }

    @Override
    protected void doClose() {
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (IOException e) {
            throw ExportImportSysout.abend(abendService, "CBIMPORT", e.toString(),
                    ExportImportSysout.reading("EXPORT-INPUT", STATUS_READ_FAILED));
        }
    }
}
