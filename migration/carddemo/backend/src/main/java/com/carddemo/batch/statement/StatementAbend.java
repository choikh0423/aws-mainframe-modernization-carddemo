package com.carddemo.batch.statement;

import com.carddemo.common.batch.AbendException;
import com.carddemo.common.batch.AbendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The CBSTM03A failure path: DISPLAY the message, DISPLAY the file status, then
 * PERFORM 9999-ABEND-PROGRAM, which displays 'ABENDING PROGRAM' and calls
 * CEE3ABD (CBSTM03A.CBL:359-361, 921-923).
 */
@Component
public class StatementAbend {

    private static final Logger LOG = LoggerFactory.getLogger(StatementAbend.class);

    private static final String PROGRAM = "CBSTM03A";

    private final AbendService abendService;

    public StatementAbend(AbendService abendService) {
        this.abendService = abendService;
    }

    /**
     * @param message the literal CBSTM03A displays, e.g. {@code ERROR READING CUSTFILE}
     * @param returnCode the FILE STATUS CBSTM03B returned
     */
    public AbendException abend(String message, String returnCode) {
        LOG.error(message);
        LOG.error("RETURN CODE: {}", returnCode);
        LOG.error("ABENDING PROGRAM");
        return abendService.abend("0001", PROGRAM, returnCode, message);
    }
}
