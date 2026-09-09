package com.carddemo.batch.intcalc;

import org.springframework.batch.item.ItemStreamException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where the sequential datasets of the interest chain live in the migrated
 * application.
 *
 * <p>{@code AWS.M2.CARDDEMO.SYSTRAN(+1)} and
 * {@code AWS.M2.CARDDEMO.TRANSACT.COMBINED(+1)} are generation data groups handed
 * from INTCALC to COMBTRAN; they stay files rather than tables because they are a
 * transport between two jobs, not a system of record (boundary B-12.2). The run
 * date stands in for the generation number, which keeps a rerun of the same
 * business date pointing at the same file.
 */
final class SystranFiles {

    private SystranFiles() {
    }

    static String systran(String workDir, String runDate) {
        return Path.of(workDir, "SYSTRAN." + runDate + ".txt").toString();
    }

    static String combined(String workDir, String runDate) {
        return Path.of(workDir, "TRANSACT.COMBINED." + runDate + ".txt").toString();
    }

    /** The z/OS allocation of a {@code DISP=(NEW,CATLG)} generation, minus the catalog. */
    static void createParentDirectory(String file) {
        Path parent = Path.of(file).toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new ItemStreamException("Cannot allocate " + file, e);
        }
    }
}
