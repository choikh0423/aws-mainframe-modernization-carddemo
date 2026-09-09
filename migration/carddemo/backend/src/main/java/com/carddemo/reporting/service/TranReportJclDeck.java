package com.carddemo.reporting.service;

import com.carddemo.reporting.port.TransactionReportJobRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * The JCL deck CORPT00C writes to the internal reader, reproduced line for line
 * from {@code JOB-DATA} (cbl:81-127) with the chosen range substituted into
 * {@code PARM-START-DATE}, {@code PARM-END-DATE} and the {@code DATEPARM} line
 * (cbl:220-221, 235-236, 429-432).
 *
 * <p>It is the documentary record of what the mainframe submits, kept because the
 * date range in it is the whole content of the S-07 → S-14 contract: whatever
 * launches the migrated job must pass the same two dates. The no-op adapter logs
 * it, and {@code TranReportJclDeckTest} pins it against the copybook.
 *
 * <p>The deck is 17 lines of exactly 80 characters, terminator included: CORPT00C
 * sets {@code END-LOOP-YES} before performing the write, so {@code /*EOF} is
 * itself written to the queue (FR-R34, cbl:498-508).
 */
public final class TranReportJclDeck {

    /** {@code JCL-RECORD PIC X(80)} (cbl:79). */
    public static final int LINE_LENGTH = 80;

    private TranReportJclDeck() {
    }

    public static List<String> build(TransactionReportJobRequest request) {
        return build(request.getStartDateText(), request.getEndDateText());
    }

    /**
     * @param startDate {@code PARM-START-DATE} text, X(10)
     * @param endDate   {@code PARM-END-DATE} text, X(10)
     */
    public static List<String> build(String startDate, String endDate) {
        String start = pad(startDate, 10);
        String end = pad(endDate, 10);

        List<String> deck = new ArrayList<>(17);
        deck.add("//TRNRPT00 JOB 'TRAN REPORT',CLASS=A,MSGCLASS=0,");
        deck.add("// NOTIFY=&SYSUID");
        deck.add("//*");
        deck.add("//JOBLIB JCLLIB ORDER=('AWS.M2.CARDDEMO.PROC')");
        deck.add("//*");
        deck.add("//STEP10 EXEC PROC=TRANREPT");
        deck.add("//*");
        deck.add("//STEP05R.SYMNAMES DD *");
        deck.add("TRAN-CARD-NUM,263,16,ZD");
        deck.add("TRAN-PROC-DT,305,10,CH");
        deck.add("PARM-START-DATE,C'" + start + "'");
        deck.add("PARM-END-DATE,C'" + end + "'");
        deck.add("/*");
        deck.add("//STEP10R.DATEPARM DD *");
        deck.add(start + " " + end);
        deck.add("/*");
        deck.add("/*EOF");

        List<String> records = new ArrayList<>(deck.size());
        for (String line : deck) {
            records.add(pad(line, LINE_LENGTH));
        }
        return records;
    }

    /** A {@code MOVE} into {@code PIC X(n)}: space-padded on the right. */
    private static String pad(String value, int length) {
        String v = value == null ? "" : value;
        if (v.length() >= length) {
            return v.substring(0, length);
        }
        return v + " ".repeat(length - v.length());
    }
}
