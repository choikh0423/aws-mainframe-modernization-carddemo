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

/** FR-S11-024 - DALYREJS(+1) with LIMIT(5) SCRATCH (POSTTRAN.jcl:34-38, DALYREJS.jcl:140-144). */
class RejectFileGenerationsTest {

    @TempDir
    Path rejectDir;

    @Test
    void allocatesANewGenerationPerRunAndKeepsFive() throws IOException {
        RejectFileGenerations generations = new RejectFileGenerations(rejectDir.toString());

        List<Path> allocated = IntStream.rangeClosed(1, 7)
                .mapToObj(run -> generations.allocateNextGeneration())
                .toList();

        assertThat(allocated).extracting(path -> path.getFileName().toString())
                .containsExactly("DALYREJS.G0001V00", "DALYREJS.G0002V00", "DALYREJS.G0003V00",
                        "DALYREJS.G0004V00", "DALYREJS.G0005V00", "DALYREJS.G0006V00",
                        "DALYREJS.G0007V00");
        assertThat(remaining()).containsExactly("DALYREJS.G0003V00", "DALYREJS.G0004V00",
                "DALYREJS.G0005V00", "DALYREJS.G0006V00", "DALYREJS.G0007V00");
    }

    @Test
    void createsTheRunDirectoryAndAnEmptyGenerationForTheRunToWriteInto() throws IOException {
        Path missing = rejectDir.resolve("run/dalyrejs");

        Path generation = new RejectFileGenerations(missing.toString()).allocateNextGeneration();

        assertThat(generation).exists().isEmptyFile();
        assertThat(generation.getParent()).isEqualTo(missing);
    }

    private List<String> remaining() throws IOException {
        try (Stream<Path> files = Files.list(rejectDir)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }
}
