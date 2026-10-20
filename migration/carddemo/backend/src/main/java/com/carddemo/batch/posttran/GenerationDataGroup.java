package com.carddemo.batch.posttran;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A {@code DEFINE GENERATIONDATAGROUP ... LIMIT(n) SCRATCH} as files in a
 * directory (D-5): every {@code (+1)} allocation is a new
 * {@code <base>.G<nnnn>V00} file, and the generations that roll off the limit are
 * deleted.
 */
final class GenerationDataGroup {

    private static final Logger log = LoggerFactory.getLogger(GenerationDataGroup.class);

    private final String baseName;
    private final int limit;
    private final Path directory;
    private final Pattern generation;

    GenerationDataGroup(String baseName, int limit, Path directory) {
        this.baseName = baseName;
        this.limit = limit;
        this.directory = directory;
        this.generation = Pattern.compile(Pattern.quote(baseName) + "\\.G(\\d{4})V00");
    }

    /**
     * Allocates {@code <base>(+1)} and scratches the generations that fall
     * outside the limit.
     *
     * @return the new, empty generation
     */
    Path allocateNextGeneration() {
        try {
            Files.createDirectories(directory);
            int next = generations().stream()
                    .map(this::sequenceOf)
                    .max(Integer::compareTo)
                    .orElse(0) + 1;

            Path allocated = directory.resolve(String.format("%s.G%04dV00", baseName, next));
            Files.createFile(allocated);
            scratchRolledOffGenerations();
            return allocated;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot allocate the next " + baseName + " generation in " + directory, e);
        }
    }

    private void scratchRolledOffGenerations() throws IOException {
        List<Path> generations = generations();
        for (Path scratched : generations.subList(0, Math.max(0, generations.size() - limit))) {
            Files.delete(scratched);
            log.info("Scratched {} generation {}", baseName, scratched.getFileName());
        }
    }

    /** Existing generations, oldest first. */
    private List<Path> generations() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(file -> generation.matcher(file.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(this::sequenceOf))
                    .toList();
        }
    }

    private int sequenceOf(Path file) {
        Matcher matcher = generation.matcher(file.getFileName().toString());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a " + baseName + " generation: " + file);
        }
        return Integer.parseInt(matcher.group(1));
    }
}
