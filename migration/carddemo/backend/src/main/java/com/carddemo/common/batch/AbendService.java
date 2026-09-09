package com.carddemo.common.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Replacement for the {@code CALL 'CEE3ABD'} abend seam used by every CardDemo
 * batch program (boundary B-01).
 *
 * <p>On z/OS the LE service terminated the task with a user abend code, which the
 * JCL saw as a non-zero condition code. Here the reason is logged with the
 * ABEND-DATA fields and an {@link AbendException} is thrown; the step fails, the
 * JobRepository records it, and the process exits non-zero. No JVM is killed
 * mid-transaction, so a restart resumes from the last committed chunk.
 */
@Service
public class AbendService {

    private static final Logger log = LoggerFactory.getLogger(AbendService.class);

    /**
     * Abends the current step.
     *
     * @param abendCode    ABEND-CODE, e.g. {@code "0999"}
     * @param abendCulprit ABEND-CULPRIT, the failing program name
     * @param abendReason  ABEND-REASON
     * @param abendMsg     ABEND-MSG
     * @throws AbendException always
     */
    public AbendException abend(String abendCode, String abendCulprit, String abendReason, String abendMsg) {
        AbendData data = new AbendData(abendCode, abendCulprit, abendReason, abendMsg);
        log.error("ABEND {}", data);
        throw new AbendException(data);
    }
}
