package com.carddemo.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;


/**
 * TRANCATG VSAM KSDS record CVTRA04Y TRAN-CAT-RECORD (RECLN 60).
 */
@Entity
@Table(name = "transaction_categories")
public class TransactionCategoryRecord {

    @EmbeddedId
    private TransactionCategoryId id;

    @Column(name = "tran_cat_type_desc", length = 50, nullable = false)
    private String tranCatTypeDesc;

    public TransactionCategoryRecord() {
    }

    public TransactionCategoryId getId() { return id; }
    public void setId(TransactionCategoryId id) { this.id = id; }

    public String getTranCatTypeDesc() { return tranCatTypeDesc; }
    public void setTranCatTypeDesc(String tranCatTypeDesc) { this.tranCatTypeDesc = tranCatTypeDesc; }
}
