package com.carddemo.batch.exportimport;

import com.carddemo.common.batch.AbendException;
import com.carddemo.common.batch.AbendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The file-status half of CBEXPORT's and CBIMPORT's SYSOUT contract: the
 * {@code 'ERROR: ...'} line a failing OPEN, READ or WRITE displays and the
 * {@code '<program>: ABENDING PROGRAM'} line 9999-ABEND-PROGRAM displays before
 * it calls CEE3ABD (CBEXPORT.cbl:200-238, 263, 304, 578; CBIMPORT.cbl:198-244,
 * 264, 315-442, 483).
 *
 * <p>The counters live in {@link ExportStatistics} and {@link ImportStatistics};
 * these are the lines an operator greps SYSOUT for when a run fails, so they go
 * to the job log verbatim, at the same points in the flow, before the abend.
 */
final class ExportImportSysout {

    /** The file status VSAM reported when a dataset could not be opened. */
    static final String STATUS_OPEN_FAILED = "35";
    /** The file status VSAM reported for a physical read error. */
    static final String STATUS_READ_FAILED = "30";
    /** The file status VSAM reported for a physical write error. */
    static final String STATUS_WRITE_FAILED = "34";

    private static final Logger log = LoggerFactory.getLogger(ExportImportSysout.class);

    private ExportImportSysout() {
    }

    static String cannotOpen(String ddName, String status) {
        return "ERROR: Cannot open " + ddName + ", Status: " + status;
    }

    static String reading(String ddName, String status) {
        return "ERROR: Reading " + ddName + ", Status: " + status;
    }

    /** Displays a line that does not abend, such as a failed error-file write. */
    static void error(String message) {
        log.error(message);
    }

    /**
     * Displays the failure and the abend line, then abends the step.
     *
     * @return never - {@link AbendService#abend} always throws
     */
    static AbendException abend(AbendService abendService, String program, String reason, String message) {
        log.error(message);
        log.error("{}: ABENDING PROGRAM", program);
        throw abendService.abend("0999", program, reason, message);
    }
}
