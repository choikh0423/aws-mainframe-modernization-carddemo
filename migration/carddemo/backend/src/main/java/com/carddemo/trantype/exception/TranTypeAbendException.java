package com.carddemo.trantype.exception;

/**
 * The CICS {@code ABEND} raised by COTRTUPC's {@code ABEND-ROUTINE}
 * (COTRTUPC.cbl:1675-1700). {@code 2000-DECIDE-ACTION} reaches it with code
 * '0001' and "UNEXPECTED DATA SCENARIO" when the COMMAREA holds a state the
 * program has no branch for (COTRTUPC.cbl:1073-1080).
 */
public class TranTypeAbendException extends RuntimeException {

    public static final String UNEXPECTED_DATA_SCENARIO = "UNEXPECTED DATA SCENARIO";

    private final String culprit;
    private final String abendCode;

    public TranTypeAbendException(String culprit, String abendCode, String message) {
        super(message);
        this.culprit = culprit;
        this.abendCode = abendCode;
    }

    public String getCulprit() {
        return culprit;
    }

    public String getAbendCode() {
        return abendCode;
    }
}
