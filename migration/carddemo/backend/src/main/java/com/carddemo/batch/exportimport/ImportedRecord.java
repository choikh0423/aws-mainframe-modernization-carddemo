package com.carddemo.batch.exportimport;

/**
 * One line CBIMPORT writes: the file it belongs to and the fixed-width record
 * itself, already padded to that file's record length.
 */
public record ImportedRecord(ImportTarget target, String line) {
}
