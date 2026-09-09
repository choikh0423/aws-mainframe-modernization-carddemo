package com.carddemo.batch.filereads;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Shared fixture access for the S-15 job tests. */
final class FileReadJobTestSupport {

    /** The ASCII unloads the H2 seed was generated from; the parity oracle. */
    static final Path ASCII = Path.of("../../../app/data/ASCII");

    private FileReadJobTestSupport() {
    }

    /** The unload lines padded back to the copybook record length. */
    static List<String> fixture(String name, int recordLength) throws IOException {
        return Files.readAllLines(ASCII.resolve(name), StandardCharsets.ISO_8859_1).stream()
                .filter(line -> !line.isBlank())
                .map(line -> CobolPicture.text(line, recordLength))
                .toList();
    }

    static List<String> sysout(Path outputDir, String jobName) throws IOException {
        return Files.readAllLines(outputDir.resolve(jobName + ".SYSOUT.txt"),
                StandardCharsets.ISO_8859_1);
    }
}
