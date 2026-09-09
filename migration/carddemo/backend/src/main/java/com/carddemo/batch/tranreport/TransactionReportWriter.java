package com.carddemo.batch.tranreport;

import com.carddemo.common.batch.AbendService;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.TransactionCategoryId;
import com.carddemo.common.domain.TransactionCategoryRecord;
import com.carddemo.common.domain.TransactionTypeRecord;
import com.carddemo.common.repository.CardXrefRepository;
import com.carddemo.common.repository.TransactionCategoryRepository;
import com.carddemo.common.repository.TransactionTypeRepository;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CBTRN03C - the daily transaction report itself (app/cbl/CBTRN03C.cbl).
 *
 * <p>One instance is the running program: it holds WS-REPORT-VARS
 * (CBTRN03C.cbl:127-137), writes the 133-byte REPORT-FILE records built from
 * CVTRA07Y, and performs the three VSAM lookups - CARDXREF, TRANTYPE, TRANCATG -
 * in the same order and with the same abend behaviour as the COBOL.
 *
 * <p>The report is rewritten from scratch on every run, as {@code OPEN OUTPUT}
 * on a {@code DISP=(NEW,CATLG,DELETE)} dataset was: the running totals and the
 * page counter are not restart state, so a restarted run re-reads the whole
 * extract rather than resuming mid-report.
 */
class TransactionReportWriter implements ItemWriter<PostedTransaction>, StepExecutionListener {

    /** WS-PAGE-SIZE, CBTRN03C.cbl:131-132. */
    private static final int PAGE_SIZE = 20;
    /** FD-REPTFILE-REC PIC X(133), CBTRN03C.cbl:84-85. */
    private static final int RECORD_LENGTH = 133;
    /** ABCODE 999 passed to CEE3ABD, CBTRN03C.cbl:626-631. */
    private static final String ABEND_CODE = "0999";
    private static final String PROGRAM = "CBTRN03C";
    /** IO-STATUS 23 rendered by 9910-DISPLAY-IO-STATUS, CBTRN03C.cbl:633-647. */
    private static final String NOT_FOUND_STATUS = "FILE STATUS IS: NNNN0023";

    private final Path reportFile;
    private final String startDate;
    private final String endDate;
    private final CardXrefRepository cardXrefs;
    private final TransactionTypeRepository transactionTypes;
    private final TransactionCategoryRepository transactionCategories;
    private final AbendService abendService;

    // WS-REPORT-VARS
    private boolean firstTime = true;
    private long lineCounter;
    private BigDecimal pageTotal = BigDecimal.ZERO;
    private BigDecimal accountTotal = BigDecimal.ZERO;
    private BigDecimal grandTotal = BigDecimal.ZERO;
    private String currCardNum = " ".repeat(16);

    /** XREF-ACCT-ID of the card being reported, from the last 1500-A-LOOKUP-XREF. */
    private long acctId;
    /** The record left in TRAN-RECORD when TRANFILE hits end of file. */
    private PostedTransaction lastRead;

    private BufferedWriter report;

    TransactionReportWriter(Path reportFile,
                            String startDate,
                            String endDate,
                            CardXrefRepository cardXrefs,
                            TransactionTypeRepository transactionTypes,
                            TransactionCategoryRepository transactionCategories,
                            AbendService abendService) {
        this.reportFile = reportFile;
        this.startDate = startDate;
        this.endDate = endDate;
        this.cardXrefs = cardXrefs;
        this.transactionTypes = transactionTypes;
        this.transactionCategories = transactionCategories;
        this.abendService = abendService;
    }

    /** 0100-REPTFILE-OPEN (CBTRN03C.cbl:394-410): OPEN OUTPUT, before any record is read. */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        try {
            Files.createDirectories(reportFile.getParent());
            report = Files.newBufferedWriter(reportFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("ERROR OPENING REPTFILE", e);
        }
    }

    @Override
    public void write(Chunk<? extends PostedTransaction> chunk) {
        for (PostedTransaction transaction : chunk) {
            read(transaction);
        }
    }

    /**
     * 9100-REPTFILE-CLOSE plus the end-of-file branch of the main read loop
     * (CBTRN03C.cbl:190-203). The step listener runs before the step's streams
     * are closed, so the trailing totals still reach the open report file; a
     * failed step (an abend) writes none of them, exactly as an abending job
     * left a partial report behind.
     */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        try {
            if (stepExecution.getStatus().isUnsuccessful()) {
                return null;
            }
            endOfFile();
            return null;
        } finally {
            close();
        }
    }

    /**
     * The body of the main loop, CBTRN03C.cbl:169-204. A record whose
     * TRAN-PROC-TS date is outside the range hits {@code NEXT SENTENCE} and
     * skips the rest of the loop, so it is neither reported nor totalled - but
     * it is still the record left in TRAN-RECORD for the end-of-file test.
     */
    private void read(PostedTransaction transaction) {
        lastRead = transaction;
        if (!inRange(transaction)) {
            return;
        }

        if (!currCardNum.equals(transaction.cardNum())) {
            if (!firstTime) {
                writeAccountTotals();
            }
            currCardNum = transaction.cardNum();
            acctId = lookupXref(transaction.cardNum());
        }
        String typeDesc = lookupTransactionType(transaction.typeCd());
        String catDesc = lookupTransactionCategory(transaction.typeCd(), transaction.catCd());
        writeTransactionReport(transaction, typeDesc, catDesc);
    }

    /**
     * End of TRANFILE (CBTRN03C.cbl:195-203). TRAN-RECORD still holds the last
     * record read, so its amount is added to the page and account totals a
     * second time before the closing Page Total and Grand Total are written -
     * and the last account never gets an Account Total line. Both are legacy
     * behaviour and are reproduced deliberately. When the last record read was
     * outside the reporting range (which includes an empty TRANFILE, where
     * TRAN-RECORD is still spaces) the whole branch is skipped and the report
     * ends without any totals.
     */
    private void endOfFile() {
        if (lastRead == null || !inRange(lastRead)) {
            return;
        }
        pageTotal = pageTotal.add(lastRead.amount());
        accountTotal = accountTotal.add(lastRead.amount());
        writePageTotals();
        writeGrandTotals();
    }

    private boolean inRange(PostedTransaction transaction) {
        return transaction.procDate().compareTo(startDate) >= 0
                && transaction.procDate().compareTo(endDate) <= 0;
    }

    /** 1100-WRITE-TRANSACTION-REPORT, CBTRN03C.cbl:274-291. */
    private void writeTransactionReport(PostedTransaction transaction, String typeDesc, String catDesc) {
        if (firstTime) {
            firstTime = false;
            writeHeaders();
        }
        if (lineCounter % PAGE_SIZE == 0) {
            writePageTotals();
            writeHeaders();
        }
        pageTotal = pageTotal.add(transaction.amount());
        accountTotal = accountTotal.add(transaction.amount());
        writeDetail(transaction, typeDesc, catDesc);
    }

    /** 1120-WRITE-HEADERS, CBTRN03C.cbl:324-342, laying out CVTRA07Y. */
    private void writeHeaders() {
        writeRecord(pad("DALYREPT", 38) + pad("Daily Transaction Report", 41)
                + "Date Range: " + pad(startDate, 10) + " to " + pad(endDate, 10));
        writeRecord("");
        writeRecord(pad("Transaction ID", 17) + pad("Account ID", 12)
                + pad("Transaction Type", 19) + pad("Tran Category", 35)
                + pad("Tran Source", 14) + " " + pad("        Amount", 16));
        writeRecord("-".repeat(RECORD_LENGTH));
        lineCounter += 4;
    }

    /** 1120-WRITE-DETAIL, CBTRN03C.cbl:361-375. */
    private void writeDetail(PostedTransaction transaction, String typeDesc, String catDesc) {
        writeRecord(pad(transaction.id(), 16) + " "
                + String.format("%011d", acctId) + " "
                + pad(transaction.typeCd(), 2) + "-" + pad(typeDesc, 15) + " "
                + String.format("%04d", transaction.catCd()) + "-" + pad(catDesc, 29) + " "
                + pad(transaction.source(), 10) + "    "
                + TranReportPictures.detailAmount(transaction.amount()) + "  ");
        lineCounter++;
    }

    /** 1110-WRITE-PAGE-TOTALS, CBTRN03C.cbl:293-305. */
    private void writePageTotals() {
        writeRecord(pad("Page Total", 11) + ".".repeat(86) + TranReportPictures.totalAmount(pageTotal));
        grandTotal = grandTotal.add(pageTotal);
        pageTotal = BigDecimal.ZERO;
        lineCounter++;
        writeRecord("-".repeat(RECORD_LENGTH));
        lineCounter++;
    }

    /** 1120-WRITE-ACCOUNT-TOTALS, CBTRN03C.cbl:306-317. */
    private void writeAccountTotals() {
        writeRecord(pad("Account Total", 13) + ".".repeat(84) + TranReportPictures.totalAmount(accountTotal));
        accountTotal = BigDecimal.ZERO;
        lineCounter++;
        writeRecord("-".repeat(RECORD_LENGTH));
        lineCounter++;
    }

    /** 1110-WRITE-GRAND-TOTALS, CBTRN03C.cbl:318-323. */
    private void writeGrandTotals() {
        writeRecord(pad("Grand Total", 11) + ".".repeat(86) + TranReportPictures.totalAmount(grandTotal));
    }

    /** 1111-WRITE-REPORT-REC, CBTRN03C.cbl:343-360. */
    private void writeRecord(String record) {
        try {
            report.write(pad(record, RECORD_LENGTH));
            report.newLine();
        } catch (IOException e) {
            throw new UncheckedIOException("ERROR WRITING REPTFILE", e);
        }
    }

    /** 1500-A-LOOKUP-XREF, CBTRN03C.cbl:484-493. */
    private long lookupXref(String cardNum) {
        CardXrefRecord xref = cardXrefs.findById(cardNum.stripTrailing()).orElse(null);
        if (xref == null) {
            throw abendService.abend(ABEND_CODE, PROGRAM, NOT_FOUND_STATUS,
                    "INVALID CARD NUMBER : " + cardNum);
        }
        return xref.getAcctId();
    }

    /** 1500-B-LOOKUP-TRANTYPE, CBTRN03C.cbl:494-503. */
    private String lookupTransactionType(String typeCd) {
        TransactionTypeRecord type = transactionTypes.findById(typeCd).orElse(null);
        if (type == null) {
            throw abendService.abend(ABEND_CODE, PROGRAM, NOT_FOUND_STATUS,
                    "INVALID TRANSACTION TYPE : " + pad(typeCd, 2));
        }
        return type.getTranTypeDesc();
    }

    /** 1500-C-LOOKUP-TRANCATG, CBTRN03C.cbl:504-513. */
    private String lookupTransactionCategory(String typeCd, int catCd) {
        TransactionCategoryRecord category = transactionCategories
                .findById(new TransactionCategoryId(typeCd, catCd))
                .orElse(null);
        if (category == null) {
            throw abendService.abend(ABEND_CODE, PROGRAM, NOT_FOUND_STATUS,
                    "INVALID TRAN CATG KEY : " + pad(typeCd, 2) + String.format("%04d", catCd));
        }
        return category.getTranCatTypeDesc();
    }

    private void close() {
        try {
            if (report != null) {
                report.close();
                report = null;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("ERROR CLOSING REPORT FILE", e);
        }
    }

    /** A COBOL MOVE to PIC X(n): pad with spaces, truncate on the right. */
    private static String pad(String value, int width) {
        String raw = value == null ? "" : value;
        return raw.length() >= width ? raw.substring(0, width)
                : raw + " ".repeat(width - raw.length());
    }
}
