package com.carddemo.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * TCATBALF VSAM KSDS record CVTRA01Y TRAN-CAT-BAL-RECORD (RECLN 50).
 */
@Entity
@Table(name = "transaction_category_balances")
public class TransactionCategoryBalanceRecord {

    @EmbeddedId
    private TransactionCategoryBalanceId id;

    @Column(name = "bal", precision = 11, scale = 2, nullable = false)
    private BigDecimal bal;

    public TransactionCategoryBalanceRecord() {
    }

    public TransactionCategoryBalanceId getId() { return id; }
    public void setId(TransactionCategoryBalanceId id) { this.id = id; }

    public BigDecimal getBal() { return bal; }
    public void setBal(BigDecimal bal) { this.bal = bal; }
}
