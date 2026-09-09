package com.carddemo.transaction.dto;

import com.carddemo.common.domain.TransactionRecord;
import com.carddemo.common.util.CobolFormat;

import java.math.BigDecimal;

/**
 * One row of the CT00 list, mirroring the fields COTRN00C's POPULATE-TRAN-DATA
 * moves onto each COTRN0A list line (COTRN00C.cbl:381-445):
 *   TRNIDnn (Tran ID), TDATEnn (date MM/DD/YY from TRAN-ORIG-TS),
 *   TDESCnn (description), TAMTnnn (signed amount PIC +99999999.99).
 * {@code date}/{@code amountDisplay} carry the legacy 3270 editing; {@code amount}
 * keeps the raw numeric for consumers that want it.
 */
public class TransactionListRow {

    private final String id;
    private final String date;
    private final String description;
    private final BigDecimal amount;
    private final String amountDisplay;

    private TransactionListRow(TransactionRecord r) {
        this.id = r.getId();
        this.date = CobolFormat.listDate(r.getOrigTs());
        this.description = r.getDescription();
        this.amount = r.getAmount();
        this.amountDisplay = CobolFormat.amountEdited(r.getAmount());
    }

    public static TransactionListRow from(TransactionRecord record) {
        return new TransactionListRow(record);
    }

    public String getId() { return id; }
    public String getDate() { return date; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public String getAmountDisplay() { return amountDisplay; }
}
