package com.carddemo.batch.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the Transaction Type record.
 * Migrated from COBOL copybook CVTRA03Y.cpy (RECLN 60).
 * VSAM KSDS key: TRAN-TYPE PIC X(02).
 */
@Entity
@Table(name = "transaction_type")
public class TransactionType {

    @Id
    @Column(name = "tran_type", length = 2)
    private String tranType;

    @Column(name = "description", length = 50)
    private String description;

    public TransactionType() {
    }

    public String getTranType() {
        return tranType;
    }

    public void setTranType(String tranType) {
        this.tranType = tranType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
