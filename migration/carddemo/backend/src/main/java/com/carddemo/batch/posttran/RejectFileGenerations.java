package com.carddemo.batch.posttran;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
 * The DALYREJS generation data group, as files in the batch output directory.
 *
 * <p>{@code POSTTRAN.jcl:34-38} allocates {@code AWS.M2.CARDDEMO.DALYREJS(+1)} for
 * every run and {@code DALYREJS.jcl:140-144} defines the group with
 * {@code LIMIT(5) SCRATCH}. Per D-5 the migrated equivalent is one
 * sequence-suffixed file per run in the run directory, keeping the five most
 * recent generations and deleting the ones that roll off.
 */
@Component
public class RejectFileGenerations {

    /** DEFINE GENERATIONDATAGROUP ... LIMIT(5), DALYREJS.jcl:142. */
    static final int LIMIT = 5;

    private static final String BASE_NAME = "DALYREJS";
    private static final Pattern GENERATION = Pattern.compile(Pattern.quote(BASE_NAME) + "\\.G(\\d{4})V00");

    private static final Logger log = LoggerFactory.getLogger(RejectFileGenerations.class);

    private final Path directory;

    public RejectFileGenerations(@Value("${carddemo.batch.posttran.reject-dir}") String rejectDir) {
        this.directory = Path.of(rejectDir);
    }

    /**
     * Allocates {@code DALYREJS(+1)} and scratches the generations that fall
     * outside the limit.
     *
     * @return the file this run writes its rejects to
     */
    public Path allocateNextGeneration() {
        try {
            Files.createDirectories(directory);
            List<Path> existing = generations();
            int next = existing.stream()
                    .map(RejectFileGenerations::sequenceOf)
                    .max(Integer::compareTo)
                    .orElse(0) + 1;

            Path generation = directory.resolve(String.format("%s.G%04dV00", BASE_NAME, next));
            Files.createFile(generation);
            scratchRolledOffGenerations();
            return generation;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot allocate the next DALYREJS generation in " + directory, e);
        }
    }

    private void scratchRolledOffGenerations() throws IOException {
        List<Path> generations = generations();
        for (Path scratched : generations.subList(0, Math.max(0, generations.size() - LIMIT))) {
            Files.delete(scratched);
            log.info("Scratched DALYREJS generation {}", scratched.getFileName());
        }
    }

    /** Existing generations, oldest first. */
    private List<Path> generations() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(file -> GENERATION.matcher(file.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(RejectFileGenerations::sequenceOf))
                    .toList();
        }
    }

    private static int sequenceOf(Path generation) {
        Matcher matcher = GENERATION.matcher(generation.getFileName().toString());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a DALYREJS generation: " + generation);
        }
        return Integer.parseInt(matcher.group(1));
    }
}
