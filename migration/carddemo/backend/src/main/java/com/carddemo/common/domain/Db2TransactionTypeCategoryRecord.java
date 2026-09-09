package com.carddemo.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;


/**
 * DB2 table CARDDEMO.TRANSACTION_TYPE_CATEGORY (ddl/TRNTYCAT.ddl).
 */
@Entity
@Table(name = "db2_transaction_type_category")
public class Db2TransactionTypeCategoryRecord {

    @EmbeddedId
    private Db2TransactionTypeCategoryId id;

    @Column(name = "trc_cat_data", length = 50, nullable = false)
    private String trcCatData;

    public Db2TransactionTypeCategoryRecord() {
    }

    public Db2TransactionTypeCategoryId getId() { return id; }
    public void setId(Db2TransactionTypeCategoryId id) { this.id = id; }

    public String getTrcCatData() { return trcCatData; }
    public void setTrcCatData(String trcCatData) { this.trcCatData = trcCatData; }
}
