package com.carddemo.batch.posttran;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-S11-030 - TRANSACT.BKUP(+1) with LIMIT(5) SCRATCH (TRANBKP.jcl:29-33, DEFGDGB.jcl:24-28). */
class TransactionBackupGenerationsTest {

    @TempDir
    Path backupDir;

    @Test
    void allocatesANewGenerationPerRunAndKeepsFive() throws IOException {
        TransactionBackupGenerations generations = new TransactionBackupGenerations(backupDir.toString());

        List<Path> allocated = IntStream.rangeClosed(1, 6)
                .mapToObj(run -> generations.allocateNextGeneration())
                .toList();

        assertThat(allocated.get(0).getFileName()).hasToString("TRANSACT.BKUP.G0001V00");
        assertThat(allocated.get(5).getFileName()).hasToString("TRANSACT.BKUP.G0006V00");
        assertThat(remaining()).containsExactly("TRANSACT.BKUP.G0002V00", "TRANSACT.BKUP.G0003V00",
                "TRANSACT.BKUP.G0004V00", "TRANSACT.BKUP.G0005V00", "TRANSACT.BKUP.G0006V00");
    }

    @Test
    void ignoresOtherGenerationGroupsInTheSameDirectory() throws IOException {
        Files.createFile(backupDir.resolve("DALYREJS.G0009V00"));
        Files.createFile(backupDir.resolve("TRANSACT.BKUP.G0009V00.tmp"));

        Path generation = new TransactionBackupGenerations(backupDir.toString()).allocateNextGeneration();

        assertThat(generation.getFileName()).hasToString("TRANSACT.BKUP.G0001V00");
        assertThat(generation).exists().isEmptyFile();
        assertThat(remaining()).contains("DALYREJS.G0009V00", "TRANSACT.BKUP.G0009V00.tmp");
    }

    private List<String> remaining() throws IOException {
        try (Stream<Path> files = Files.list(backupDir)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }
}
