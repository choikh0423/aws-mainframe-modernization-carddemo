package com.carddemo.statements.dto;

import com.carddemo.statements.StatementArchiveRecord;
import com.carddemo.statements.StatementFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One row of the statement list. The card number is masked to its last four digits. */
public record StatementSummary(long stmtId, String cycle, String cardNum, int txnCount,
                               BigDecimal totalAmount, LocalDateTime generatedTs) {

    public static StatementSummary from(StatementArchiveRecord record) {
        return new StatementSummary(record.getStmtId(), record.getCycle(),
                StatementFormat.maskCard(record.getCardNum()), record.getTxnCount(),
                record.getTotalAmount(), record.getGeneratedTs());
    }
}
