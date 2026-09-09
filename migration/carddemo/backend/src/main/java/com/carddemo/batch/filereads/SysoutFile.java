package com.carddemo.batch.filereads;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The {@code SYSOUT DD SYSOUT=*} of an S-15 job: every {@code DISPLAY} the program
 * issued, in order, one per line (FR-G4).
 *
 * <p>The lines are written to {@code <output-dir>/<JOB>.SYSOUT.txt} and mirrored to the
 * job log so a run shows the same print stream the JES spool held.
 */
public final class SysoutFile implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SysoutFile.class);

    private final Path path;
    private final BufferedWriter writer;

    private SysoutFile(Path path, BufferedWriter writer) {
        this.path = path;
        this.writer = writer;
    }

    /** Allocates the print file, replacing the output of any previous run. */
    public static SysoutFile open(Path path) {
        try {
            Files.createDirectories(path.getParent());
            return new SysoutFile(path, Files.newBufferedWriter(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot allocate SYSOUT " + path, e);
        }
    }

    public Path path() {
        return path;
    }

    public void display(String line) {
        try {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write SYSOUT " + path, e);
        }
        log.info("{}", line);
    }

    @Override
    public void close() {
        try {
            writer.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot close SYSOUT " + path, e);
        }
    }
}
