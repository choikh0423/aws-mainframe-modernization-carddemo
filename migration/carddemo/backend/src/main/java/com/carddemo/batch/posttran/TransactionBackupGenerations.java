package com.carddemo.batch.posttran;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * The {@code AWS.M2.CARDDEMO.TRANSACT.BKUP} generation data group TRANBKP writes
 * to ({@code TRANBKP.jcl:29-33}), defined {@code LIMIT(5) SCRATCH} by
 * {@code DEFGDGB.jcl:24-28}. Same file convention as DALYREJS (D-5).
 */
@Component
public class TransactionBackupGenerations {

    /** DEFINE GENERATIONDATAGROUP (NAME(AWS.M2.CARDDEMO.TRANSACT.BKUP) LIMIT(5) SCRATCH). */
    static final int LIMIT = 5;

    private final GenerationDataGroup group;

    public TransactionBackupGenerations(@Value("${carddemo.batch.posttran.backup-dir}") String backupDir) {
        this.group = new GenerationDataGroup("TRANSACT.BKUP", LIMIT, Path.of(backupDir));
    }

    /** Allocates {@code TRANSACT.BKUP(+1)}. */
    public Path allocateNextGeneration() {
        return group.allocateNextGeneration();
    }
}
