package com.carddemo.batch.filereads;

import com.carddemo.common.batch.AbendService;
import org.springframework.stereotype.Component;

/**
 * The abend sequence shared by the four S-15 programs (FR-G7): the program's error
 * literal, the file-status line, {@code ABENDING PROGRAM}, then the B-01 abend seam
 * with {@code ABCODE = 999} (`CBACT01C.cbl:406-410`).
 */
@Component
public class FileReadAbend {

    /** {@code MOVE 999 TO ABCODE} rendered as the four-character ABEND-CODE. */
    public static final String ABEND_CODE = "0999";

    /** {@code ABENDING PROGRAM} (`CBACT01C.cbl:407`). */
    public static final String ABENDING_PROGRAM = "ABENDING PROGRAM";

    /**
     * File status reported for a failure of the underlying Java I/O or JDBC call:
     * COBOL status 30, "permanent error", the status a VSAM data set gave for the
     * same class of failure.
     */
    public static final String PERMANENT_ERROR = "30";

    private final AbendService abendService;

    public FileReadAbend(AbendService abendService) {
        this.abendService = abendService;
    }

    /**
     * Displays the three abend lines on the job's SYSOUT and abends.
     *
     * @param sysout     the print file of the running step
     * @param program    the COBOL program being reproduced, used as ABEND-CULPRIT
     * @param message    the program's error literal, already carrying the file status
     *                   where the COBOL {@code DISPLAY} appended it
     * @param fileStatus the two-byte file status to render (FR-G8)
     * @return never; the call always throws
     */
    public RuntimeException abend(SysoutFile sysout, String program, String message, String fileStatus) {
        String statusLine = IoStatusFormatter.line(fileStatus);
        sysout.display(message);
        sysout.display(statusLine);
        sysout.display(ABENDING_PROGRAM);
        return abendService.abend(ABEND_CODE, program, message, statusLine);
    }
}
