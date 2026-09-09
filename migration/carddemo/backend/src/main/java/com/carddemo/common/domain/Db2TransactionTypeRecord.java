package com.carddemo.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


/**
 * DB2 table CARDDEMO.TRANSACTION_TYPE (app/app-transaction-type-db2/ddl/TRNTYPE.ddl).
 */
@Entity
@Table(name = "db2_transaction_type")
public class Db2TransactionTypeRecord {

    @Id
    @Column(name = "tr_type", length = 2, nullable = false)
    private String trType;

    @Column(name = "tr_description", length = 50, nullable = false)
    private String trDescription;

    public Db2TransactionTypeRecord() {
    }

    public String getTrType() { return trType; }
    public void setTrType(String trType) { this.trType = trType; }

    public String getTrDescription() { return trDescription; }
    public void setTrDescription(String trDescription) { this.trDescription = trDescription; }
}
