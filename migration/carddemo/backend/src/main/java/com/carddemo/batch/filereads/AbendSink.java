package com.carddemo.batch.filereads;

/**
 * The abend path of the step currently running, so a reader and a writer of the same
 * step report their file-status failures on the same SYSOUT with the same culprit.
 */
public interface AbendSink {

    /**
     * @param message    the program's error literal
     * @param fileStatus the two-byte COBOL file status
     * @return never; the call always throws
     */
    RuntimeException abend(String message, String fileStatus);
}
