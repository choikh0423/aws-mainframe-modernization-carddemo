package com.carddemo.batch.statement;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;

import java.util.List;

/**
 * 1000-MAINLINE (CBSTM03A.CBL:316-342): the CARDXREF driving read plus the
 * CUSTFILE and ACCTFILE lookups and the WS-TRNX-TABLE scan that make up one
 * statement.
 *
 * <p>{@link #open} performs the file-open sequence of 0000-START and loads the
 * transaction table (8100 / 8500); {@link #close} performs the closes of
 * 9100-9400. Like the legacy program the reader has no restart position: a
 * restarted STEP040 regenerates the whole report from the first CARDXREF record,
 * which is why the step's writers truncate their files.
 */
public class StatementItemReader implements ItemStreamReader<Statement> {

    private final StatementFileAccess files;
    private final StatementAbend abend;

    private final StatementFileArea area = new StatementFileArea();

    private TransactionIndex index;
    private boolean endOfFile;

    public StatementItemReader(StatementFileAccess files, StatementAbend abend) {
        this.files = files;
        this.abend = abend;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        endOfFile = false;
        index = TransactionIndex.load(files, abend);
        open(StatementFileArea.DD_XREFFILE);
        open(StatementFileArea.DD_CUSTFILE);
        open(StatementFileArea.DD_ACCTFILE);
    }

    @Override
    public Statement read() {
        if (endOfFile) {
            return null;
        }
        String xrefRecord = readNextXref();
        if (xrefRecord == null) {
            return null;
        }
        String cardNumber = XrefLayout.cardNumber(xrefRecord);
        String customerRecord = readKeyed(StatementFileArea.DD_CUSTFILE,
                XrefLayout.customerId(xrefRecord), "ERROR READING CUSTFILE");
        String accountRecord = readKeyed(StatementFileArea.DD_ACCTFILE,
                XrefLayout.accountId(xrefRecord), "ERROR READING ACCTFILE");
        List<String> transactions = index.transactionsFor(cardNumber);
        return new Statement(cardNumber, customerRecord, accountRecord, transactions);
    }

    @Override
    public void close() throws ItemStreamException {
        close(StatementFileArea.DD_TRNXFILE);
        close(StatementFileArea.DD_XREFFILE);
        close(StatementFileArea.DD_CUSTFILE);
        close(StatementFileArea.DD_ACCTFILE);
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        // No restart position: see the class comment.
    }

    /** 1000-XREFFILE-GET-NEXT (CBSTM03A.CBL:345-366). */
    private String readNextXref() {
        area.setDd(StatementFileArea.DD_XREFFILE);
        area.setOper(StatementFileArea.OPER_READ);
        files.call(area);
        if (StatementFileArea.RC_OK.equals(area.getRc())) {
            return area.getData();
        }
        if (StatementFileArea.RC_END_OF_FILE.equals(area.getRc())) {
            endOfFile = true;
            return null;
        }
        throw abend.abend("ERROR READING XREFFILE", area.getRc());
    }

    /** 2000-CUSTFILE-GET / 3000-ACCTFILE-GET (CBSTM03A.CBL:368-414). */
    private String readKeyed(String dd, String key, String message) {
        area.setDd(dd);
        area.setOper(StatementFileArea.OPER_READ_KEY);
        area.setKey(key);
        area.setKeyLength(key.length());
        files.call(area);
        if (!StatementFileArea.RC_OK.equals(area.getRc())) {
            throw abend.abend(message, area.getRc());
        }
        return area.getData();
    }

    private void open(String dd) {
        area.setDd(dd);
        area.setOper(StatementFileArea.OPER_OPEN);
        files.call(area);
        if (!StatementFileArea.RC_OK.equals(area.getRc()) && !"04".equals(area.getRc())) {
            throw abend.abend("ERROR OPENING " + dd, area.getRc());
        }
    }

    private void close(String dd) {
        area.setDd(dd);
        area.setOper(StatementFileArea.OPER_CLOSE);
        files.call(area);
        if (!StatementFileArea.RC_OK.equals(area.getRc()) && !"04".equals(area.getRc())) {
            throw abend.abend("ERROR CLOSING " + dd, area.getRc());
        }
    }
}
