package com.carddemo.batch.exportimport;

import com.carddemo.common.batch.AbendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.Map;

/**
 * Writes CBIMPORT's six output files (CBIMPORT.cbl:2300…2750). Records go out in
 * input order, each padded to its copybook length and terminated with a newline
 * so the files are interchangeable with the estate's ASCII unloads.
 *
 * <p>Each file's committed length is kept in the step's ExecutionContext and a
 * restart truncates back to it, which reproduces the legacy
 * {@code DISP=(NEW,CATLG,DELETE)} guarantee that a run's output contains that
 * run's records only.
 *
 * <p>Failure handling follows the source exactly, quirk included: a failed write
 * to a data file abends the step, a failed write to the error file only issues
 * {@code 'ERROR: Writing error record, Status: '} and carries on, and the
 * errors-written counter is incremented either way (CBIMPORT.cbl:437-446).
 */
public class ImportOutputItemWriter implements ItemStreamWriter<ImportedRecord> {

    static final String POSITION_KEY_PREFIX = "carddemo.exportimport.import.position.";

    /** The file status VSAM reported when a dataset could not be opened. */
    static final String STATUS_OPEN_FAILED = "35";
    /** The file status VSAM reported for a physical write error. */
    static final String STATUS_WRITE_FAILED = "34";

    private static final Logger log = LoggerFactory.getLogger(ImportOutputItemWriter.class);

    private final Path directory;
    private final AbendService abendService;
    private final ImportStatistics statistics;
    private final Map<ImportTarget, FileChannel> channels = new EnumMap<>(ImportTarget.class);

    public ImportOutputItemWriter(Path directory, AbendService abendService, ImportStatistics statistics) {
        this.directory = directory;
        this.abendService = abendService;
        this.statistics = statistics;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        for (ImportTarget target : ImportTarget.values()) {
            long position = executionContext.getLong(positionKey(target), 0L);
            try {
                FileChannel channel = FileChannel.open(directory.resolve(target.fileName()),
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                channel.truncate(position);
                channel.position(position);
                channels.put(target, channel);
            } catch (IOException e) {
                throw abendService.abend("0999", "CBIMPORT", e.toString(),
                        "ERROR: Cannot open " + target.ddName() + ", Status: " + STATUS_OPEN_FAILED);
            }
        }
    }

    @Override
    public void write(Chunk<? extends ImportedRecord> chunk) {
        for (ImportedRecord record : chunk) {
            ImportTarget target = record.target();
            try {
                channels.get(target).write(ByteBuffer.wrap(
                        (record.line() + "\n").getBytes(StandardCharsets.ISO_8859_1)));
            } catch (IOException e) {
                if (target != ImportTarget.ERROR) {
                    throw abendService.abend("0999", "CBIMPORT", e.toString(),
                            target.writeErrorMessage(STATUS_WRITE_FAILED));
                }
                log.error(target.writeErrorMessage(STATUS_WRITE_FAILED));
            }
            statistics.recordWritten(target);
        }
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        channels.forEach((target, channel) -> {
            try {
                channel.force(false);
                executionContext.putLong(positionKey(target), channel.position());
            } catch (IOException e) {
                throw abendService.abend("0999", "CBIMPORT", e.toString(),
                        target.writeErrorMessage(STATUS_WRITE_FAILED));
            }
        });
    }

    @Override
    public void close() throws ItemStreamException {
        for (FileChannel channel : channels.values()) {
            try {
                channel.close();
            } catch (IOException e) {
                throw new ItemStreamException(e);
            }
        }
        channels.clear();
    }

    private static String positionKey(ImportTarget target) {
        return POSITION_KEY_PREFIX + target.ddName();
    }
}
