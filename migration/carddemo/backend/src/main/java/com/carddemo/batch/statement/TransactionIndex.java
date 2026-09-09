package com.carddemo.batch.statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * WS-TRNX-TABLE (CBSTM03A.CBL:225-233) and the paragraphs that fill and search
 * it: 8100-TRNXFILE-OPEN's priming read, 8500-READTRNX-READ's grouping loop and
 * 4000-TRNXFILE-GET's scan.
 *
 * <p>The table holds 51 cards of 10 transactions. The legacy program has no bound
 * check and writes past the occurrence; here the excess is dropped and logged so
 * the failure is visible instead of corrupting memory.
 */
final class TransactionIndex {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionIndex.class);

    static final int MAX_CARDS = 51;
    static final int MAX_TRANSACTIONS = 10;

    private final List<String> cardNumbers = new ArrayList<>();
    private final List<List<String>> transactions = new ArrayList<>();

    private String saveCard;
    private int cardCount;
    private int tranCount;
    private int overflow;

    /**
     * 8100-TRNXFILE-OPEN + 8500-READTRNX-READ: open TRNXFILE, prime on the first
     * record and group every following record by card number.
     */
    static TransactionIndex load(StatementFileAccess files, StatementAbend abend) {
        StatementFileArea area = new StatementFileArea();
        area.setDd(StatementFileArea.DD_TRNXFILE);
        area.setOper(StatementFileArea.OPER_OPEN);
        files.call(area);
        if (!isOpened(area.getRc())) {
            throw abend.abend("ERROR OPENING TRNXFILE", area.getRc());
        }

        area.setOper(StatementFileArea.OPER_READ);
        files.call(area);
        if (!isOpened(area.getRc())) {
            throw abend.abend("ERROR READING TRNXFILE", area.getRc());
        }

        TransactionIndex index = new TransactionIndex();
        index.prime(TrnxLayout.cardNumber(area.getData()));
        String record = area.getData();
        while (true) {
            index.store(record);
            area.setOper(StatementFileArea.OPER_READ);
            files.call(area);
            if (StatementFileArea.RC_OK.equals(area.getRc())) {
                record = area.getData();
            } else if (StatementFileArea.RC_END_OF_FILE.equals(area.getRc())) {
                break;
            } else {
                throw abend.abend("ERROR READING TRNXFILE", area.getRc());
            }
        }
        index.finish();
        return index;
    }

    private static boolean isOpened(String returnCode) {
        return StatementFileArea.RC_OK.equals(returnCode) || "04".equals(returnCode);
    }

    /** MOVE TRNX-CARD-NUM TO WS-SAVE-CARD / MOVE 1 TO CR-CNT / MOVE 0 TO TR-CNT. */
    void prime(String cardNumber) {
        saveCard = cardNumber;
        cardCount = 1;
        tranCount = 0;
        cardNumbers.add(cardNumber);
        transactions.add(new ArrayList<>());
    }

    /** One pass of 8500-READTRNX-READ over the record currently in TRNX-RECORD. */
    void store(String trnxRecord) {
        String cardNumber = TrnxLayout.cardNumber(trnxRecord);
        if (cardNumber.equals(saveCard)) {
            tranCount++;
        } else {
            cardCount++;
            tranCount = 1;
            if (cardCount <= MAX_CARDS) {
                cardNumbers.add(cardNumber);
                transactions.add(new ArrayList<>());
            }
        }
        saveCard = cardNumber;

        if (cardCount > MAX_CARDS || tranCount > MAX_TRANSACTIONS) {
            overflow++;
            return;
        }
        cardNumbers.set(cardCount - 1, cardNumber);
        transactions.get(cardCount - 1).add(trnxRecord);
    }

    private void finish() {
        if (overflow > 0) {
            LOG.warn("WS-TRNX-TABLE holds {} cards of {} transactions; {} work record(s) did not fit",
                    MAX_CARDS, MAX_TRANSACTIONS, overflow);
        }
    }

    /**
     * 4000-TRNXFILE-GET's scan (CBSTM03A.CBL:417-431): walk the table from the
     * first entry and stop at the first card number greater than the xref card,
     * which both the table and CARDXREF being in ascending order allow.
     */
    List<String> transactionsFor(String xrefCardNumber) {
        List<String> found = new ArrayList<>();
        for (int slot = 0; slot < Math.min(cardCount, cardNumbers.size()); slot++) {
            String cardNumber = cardNumbers.get(slot);
            if (cardNumber.compareTo(xrefCardNumber) > 0) {
                break;
            }
            if (cardNumber.equals(xrefCardNumber)) {
                found.addAll(transactions.get(slot));
            }
        }
        return found;
    }

    /** CR-CNT, the number of distinct card numbers seen. */
    int cardCount() {
        return cardCount;
    }

    /** The number of work records that did not fit in the table. */
    int overflowCount() {
        return overflow;
    }
}
