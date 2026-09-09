package com.carddemo.pendingauth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 9500-LOG-ERROR (COPAUA0C.cbl:988-1017): CP00 formats {@code ERROR-LOG-RECORD}
 * ({@code CCPAUERY.cpy}) and writes it to the CICS system log TD queue
 * {@code CSSL}. The migrated driver writes the same fixed-width record to the
 * application log, so operations still greps the same layout.
 */
@Component
public class AuthorizationErrorLog {

    private static final Logger LOG = LoggerFactory.getLogger(AuthorizationErrorLog.class);

    private static final DateTimeFormatter ERR_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter ERR_TIME = DateTimeFormatter.ofPattern("HHmmss");

    /** {@code ERR-APPLICATION} / {@code ERR-PROGRAM}: CP00 / COPAUA0C. */
    private static final String APPLICATION = "CP00";
    private static final String PROGRAM = "COPAUA0C";

    /** {@code ERR-LEVEL}. */
    public enum Level {
        LOG("L"), INFO("I"), WARNING("W"), CRITICAL("C");

        private final String code;

        Level(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** {@code ERR-SUBSYSTEM}. */
    public enum Subsystem {
        APP("A"), CICS("C"), IMS("I"), DB2("D"), MQ("M"), FILE("F");

        private final String code;

        Subsystem(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** One {@code ERROR-LOG-RECORD} occurrence. */
    public record Entry(String location,
                        Level level,
                        Subsystem subsystem,
                        String code1,
                        String code2,
                        String message,
                        String eventKey) {
    }

    private final Clock clock;

    public AuthorizationErrorLog(Clock clock) {
        this.clock = clock;
    }

    public void write(Entry entry) {
        LOG.warn("{}", format(entry));
    }

    /** The 126-byte {@code ERROR-LOG-RECORD} image. */
    public String format(Entry entry) {
        LocalDateTime now = LocalDateTime.now(clock);
        return pad(now.format(ERR_DATE), 6)
                + pad(now.format(ERR_TIME), 6)
                + pad(APPLICATION, 8)
                + pad(PROGRAM, 8)
                + pad(entry.location(), 4)
                + entry.level().code()
                + entry.subsystem().code()
                + pad(entry.code1(), 9)
                + pad(entry.code2(), 9)
                + pad(entry.message(), 50)
                + pad(entry.eventKey(), 20);
    }

    private static String pad(String value, int width) {
        String v = value == null ? "" : value;
        if (v.length() > width) {
            return v.substring(0, width);
        }
        return v + " ".repeat(width - v.length());
    }
}
