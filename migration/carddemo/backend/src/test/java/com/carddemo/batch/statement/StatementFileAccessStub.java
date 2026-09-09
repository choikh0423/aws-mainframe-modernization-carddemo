package com.carddemo.batch.statement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A CBSTM03B that serves records from memory, so the CBSTM03A behaviour can be
 * tested without a database.
 */
class StatementFileAccessStub extends StatementFileAccess {

    private final List<String> transactions;
    private final List<String> xrefs;
    private final Map<String, String> customers = new HashMap<>();
    private final Map<String, String> accounts = new HashMap<>();
    private final List<String> calls = new ArrayList<>();

    private Iterator<String> transactionCursor;
    private Iterator<String> xrefCursor;
    private String openFailureDd;

    StatementFileAccessStub(List<String> transactions) {
        this(transactions, List.of());
    }

    StatementFileAccessStub(List<String> transactions, List<String> xrefs) {
        super(null, null, null, null);
        this.transactions = transactions;
        this.xrefs = xrefs;
    }

    StatementFileAccessStub withCustomer(String key, String record) {
        customers.put(key, record);
        return this;
    }

    StatementFileAccessStub withAccount(String key, String record) {
        accounts.put(key, record);
        return this;
    }

    StatementFileAccessStub failingToOpen(String dd) {
        openFailureDd = dd;
        return this;
    }

    List<String> calls() {
        return calls;
    }

    @Override
    public void call(StatementFileArea area) {
        calls.add(area.getDd() + ":" + area.getOper());
        switch (area.getDd()) {
            case StatementFileArea.DD_TRNXFILE -> {
                if (area.getOper() == StatementFileArea.OPER_OPEN) {
                    transactionCursor = transactions.iterator();
                }
                sequential(area, transactionCursor);
            }
            case StatementFileArea.DD_XREFFILE -> {
                if (area.getOper() == StatementFileArea.OPER_OPEN) {
                    xrefCursor = xrefs.iterator();
                }
                sequential(area, xrefCursor);
            }
            case StatementFileArea.DD_CUSTFILE -> keyed(area, customers);
            case StatementFileArea.DD_ACCTFILE -> keyed(area, accounts);
            default -> {
                // Unknown DD names leave the return code untouched, as CBSTM03B does.
            }
        }
    }

    private void sequential(StatementFileArea area, Iterator<String> cursor) {
        if (area.getOper() == StatementFileArea.OPER_READ) {
            if (cursor != null && cursor.hasNext()) {
                area.setData(cursor.next());
                area.setRc(StatementFileArea.RC_OK);
            } else {
                area.setRc(StatementFileArea.RC_END_OF_FILE);
            }
            return;
        }
        area.setRc(failsToOpen(area) ? StatementFileArea.RC_OPEN_FAILED : StatementFileArea.RC_OK);
    }

    private void keyed(StatementFileArea area, Map<String, String> records) {
        if (area.getOper() == StatementFileArea.OPER_READ_KEY) {
            String record = records.get(area.keyValue().trim());
            if (record == null) {
                area.setRc(StatementFileArea.RC_NOT_FOUND);
            } else {
                area.setData(record);
                area.setRc(StatementFileArea.RC_OK);
            }
            return;
        }
        area.setRc(failsToOpen(area) ? StatementFileArea.RC_OPEN_FAILED : StatementFileArea.RC_OK);
    }

    private boolean failsToOpen(StatementFileArea area) {
        return area.getOper() == StatementFileArea.OPER_OPEN && area.getDd().equals(openFailureDd);
    }
}
