package com.carddemo.batch.exportimport;

import com.carddemo.common.batch.AbendService;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Writes EXPFILE: fixed 500-byte records, no delimiter, exactly as the legacy
 * RECFM=F VSAM cluster held them.
 *
 * <p>The sequence number is stamped here rather than in the processor because
 * CBEXPORT numbers records as it writes them (CBEXPORT.cbl:276-277) and because
 * the byte position of the file is what makes the step restartable: the
 * position after the last committed chunk is saved in the step's
 * ExecutionContext, and a restart truncates back to it, so the numbering
 * continues instead of restarting at 1 or leaving a half-written chunk behind.
 *
 * <p>An I/O failure is the migrated form of the legacy
 * {@code 'ERROR: Writing export record, Status: '} + abend path
 * (CBEXPORT.cbl:303-307).
 */
public class ExportFileItemWriter implements ItemStreamWriter<byte[]> {

    static final String POSITION_KEY = "carddemo.exportimport.export.position";

    static final String STATUS_OPEN_FAILED = ExportImportSysout.STATUS_OPEN_FAILED;
    static final String STATUS_WRITE_FAILED = ExportImportSysout.STATUS_WRITE_FAILED;

    static final String WRITE_ERROR_MESSAGE =
            "ERROR: Writing export record, Status: " + STATUS_WRITE_FAILED;

    private final Path file;
    private final AbendService abendService;

    private FileChannel channel;
    private long sequenceNumber;

    public ExportFileItemWriter(Path file, AbendService abendService) {
        this.file = file;
        this.abendService = abendService;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        long position = executionContext.getLong(POSITION_KEY, 0L);
        try {
            channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            channel.truncate(position);
            channel.position(position);
        } catch (IOException e) {
            throw ExportImportSysout.abend(abendService, "CBEXPORT", e.toString(),
                    ExportImportSysout.cannotOpen("EXPORT-OUTPUT", STATUS_OPEN_FAILED));
        }
        sequenceNumber = position / ExportRecordCodec.RECORD_LENGTH;
    }

    @Override
    public void write(Chunk<? extends byte[]> chunk) {
        for (byte[] record : chunk) {
            ExportRecordCodec.putSequenceNumber(record, ++sequenceNumber);
            try {
                channel.write(ByteBuffer.wrap(record));
            } catch (IOException e) {
                throw ExportImportSysout.abend(abendService, "CBEXPORT", e.toString(),
                        WRITE_ERROR_MESSAGE);
            }
        }
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        try {
            channel.force(false);
            executionContext.putLong(POSITION_KEY, channel.position());
        } catch (IOException e) {
            throw ExportImportSysout.abend(abendService, "CBEXPORT", e.toString(),
                    WRITE_ERROR_MESSAGE);
        }
    }

    @Override
    public void close() throws ItemStreamException {
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (IOException e) {
            throw new ItemStreamException(e);
        }
    }
}
