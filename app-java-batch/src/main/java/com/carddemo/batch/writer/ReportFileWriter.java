package com.carddemo.batch.writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;

/**
 * Writes formatted transaction report to a text file.
 * Ported from CBTRN03C.cbl paragraphs:
 *   1100-WRITE-TRANSACTION-REPORT
 *   1110-WRITE-PAGE-TOTALS
 *   1120-WRITE-ACCOUNT-TOTALS
 *   1120-WRITE-DETAIL
 */
@Component
public class ReportFileWriter {

    private static final Logger log = LoggerFactory.getLogger(ReportFileWriter.class);

    @Value("${carddemo.files.transaction-report:output/tranrept.txt}")
    private String reportFilePath;

    private PrintWriter writer;
    private int pageNumber;
    private int lineCount;
    private int linesPerPage = 60;

    public void open() throws IOException {
        writer = new PrintWriter(new BufferedWriter(new FileWriter(reportFilePath)));
        pageNumber = 0;
        lineCount = linesPerPage; // force new page on first write
    }

    public void close() {
        if (writer != null) {
            writer.flush();
            writer.close();
        }
    }

    public void writePageHeader(String startDate, String endDate) {
        pageNumber++;
        lineCount = 0;
        writer.println();
        writer.printf("  CARDDEMO TRANSACTION REPORT                          PAGE: %5d%n", pageNumber);
        writer.printf("  DATE RANGE: %s TO %s%n", startDate, endDate);
        writer.println("  " + "-".repeat(100));
        writer.printf("  %-16s %-16s %-2s %4s %-30s %12s %-10s%n",
                "CARD NUMBER", "TRAN ID", "TY", "CAT", "DESCRIPTION", "AMOUNT", "DATE");
        writer.println("  " + "-".repeat(100));
        lineCount = 5;
    }

    public void writeDetailLine(String cardNum, String tranId, String tranTypeCd,
                                 Integer tranCatCd, String tranDesc,
                                 BigDecimal tranAmt, String tranDate) {
        if (lineCount >= linesPerPage) {
            writePageHeader("", "");
        }
        writer.printf("  %-16s %-16s %-2s %04d %-30s %12.2f %-10s%n",
                cardNum != null ? cardNum.trim() : "",
                tranId != null ? tranId.trim() : "",
                tranTypeCd != null ? tranTypeCd : "",
                tranCatCd != null ? tranCatCd : 0,
                tranDesc != null ? (tranDesc.length() > 30 ? tranDesc.substring(0, 30) : tranDesc) : "",
                tranAmt != null ? tranAmt : BigDecimal.ZERO,
                tranDate != null ? tranDate : "");
        lineCount++;
    }

    public void writeAccountTotal(String cardNum, BigDecimal accountTotal, int transactionCount) {
        writer.println("  " + "-".repeat(60));
        writer.printf("  ACCOUNT TOTAL FOR %-16s: %12.2f  (%d transactions)%n",
                cardNum != null ? cardNum.trim() : "",
                accountTotal != null ? accountTotal : BigDecimal.ZERO,
                transactionCount);
        writer.println();
        lineCount += 3;
    }

    public void writeGrandTotal(BigDecimal grandTotal, int totalTransactions) {
        writer.println();
        writer.println("  " + "=".repeat(100));
        writer.printf("  GRAND TOTAL: %12.2f  (%d transactions)%n",
                grandTotal != null ? grandTotal : BigDecimal.ZERO,
                totalTransactions);
        writer.println("  " + "=".repeat(100));
    }

    public int getPageNumber() {
        return pageNumber;
    }
}
