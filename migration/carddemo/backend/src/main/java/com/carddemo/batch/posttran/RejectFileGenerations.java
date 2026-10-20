package com.carddemo.batch.posttran;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

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

    private final GenerationDataGroup group;

    public RejectFileGenerations(@Value("${carddemo.batch.posttran.reject-dir}") String rejectDir) {
        this.group = new GenerationDataGroup("DALYREJS", LIMIT, Path.of(rejectDir));
    }

    /**
     * Allocates {@code DALYREJS(+1)} and scratches the generations that fall
     * outside the limit.
     *
     * @return the file this run writes its rejects to
     */
    public Path allocateNextGeneration() {
        return group.allocateNextGeneration();
    }
}
