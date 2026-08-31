package com.carddemo.batch.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * JPA entity for the Transaction Category record.
 * Migrated from COBOL copybook CVTRA04Y.cpy (RECLN 60).
 * Composite key: (tranTypeCd, tranCatCd).
 */
@Entity
@Table(name = "transaction_category")
@IdClass(TransactionCategoryId.class)
public class TransactionCategory {

    @Id
    @Column(name = "tran_type_cd", length = 2)
    private String tranTypeCd;

    @Id
    @Column(name = "tran_cat_cd")
    private Integer tranCatCd;

    @Column(name = "description", length = 54)
    private String description;

    public TransactionCategory() {
    }

    public String getTranTypeCd() {
        return tranTypeCd;
    }

    public void setTranTypeCd(String tranTypeCd) {
        this.tranTypeCd = tranTypeCd;
    }

    public Integer getTranCatCd() {
        return tranCatCd;
    }

    public void setTranCatCd(Integer tranCatCd) {
        this.tranCatCd = tranCatCd;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
