package com.carddemo.statements;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One archived statement: the text and HTML rendering of one card's statement
 * for one cycle ({@code YYYY-MM}). Table {@code statement_archive}, created by
 * V2100; {@code (card_num, cycle)} is unique.
 *
 * <p>Stream-private to S-21 OnlineStatements.
 */
@Entity
@Table(name = "statement_archive")
public class StatementArchiveRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stmt_id", nullable = false, updatable = false)
    private Long stmtId;

    @Column(name = "acct_id", nullable = false, precision = 11)
    private Long acctId;

    @Column(name = "card_num", nullable = false, length = 16)
    private String cardNum;

    @Column(name = "cycle", nullable = false, length = 7, columnDefinition = "CHAR(7)")
    private String cycle;

    @Column(name = "generated_ts", nullable = false)
    private LocalDateTime generatedTs;

    @Column(name = "txn_count", nullable = false)
    private Integer txnCount;

    @Column(name = "total_amount", nullable = false, precision = 11, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "text_body", nullable = false, columnDefinition = "TEXT")
    private String textBody;

    @Column(name = "html_body", nullable = false, columnDefinition = "TEXT")
    private String htmlBody;

    public Long getStmtId() {
        return stmtId;
    }

    public void setStmtId(Long stmtId) {
        this.stmtId = stmtId;
    }

    public Long getAcctId() {
        return acctId;
    }

    public void setAcctId(Long acctId) {
        this.acctId = acctId;
    }

    public String getCardNum() {
        return cardNum;
    }

    public void setCardNum(String cardNum) {
        this.cardNum = cardNum;
    }

    public String getCycle() {
        return cycle;
    }

    public void setCycle(String cycle) {
        this.cycle = cycle;
    }

    public LocalDateTime getGeneratedTs() {
        return generatedTs;
    }

    public void setGeneratedTs(LocalDateTime generatedTs) {
        this.generatedTs = generatedTs;
    }

    public Integer getTxnCount() {
        return txnCount;
    }

    public void setTxnCount(Integer txnCount) {
        this.txnCount = txnCount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getTextBody() {
        return textBody;
    }

    public void setTextBody(String textBody) {
        this.textBody = textBody;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public void setHtmlBody(String htmlBody) {
        this.htmlBody = htmlBody;
    }
}
