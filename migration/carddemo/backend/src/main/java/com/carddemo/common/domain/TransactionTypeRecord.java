package com.carddemo.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


/**
 * TRANTYPE VSAM KSDS record CVTRA03Y TRAN-TYPE-RECORD (RECLN 60).
 */
@Entity
@Table(name = "transaction_types")
public class TransactionTypeRecord {

    @Id
    @Column(name = "tran_type", length = 2, nullable = false)
    private String tranType;

    @Column(name = "tran_type_desc", length = 50, nullable = false)
    private String tranTypeDesc;

    public TransactionTypeRecord() {
    }

    public String getTranType() { return tranType; }
    public void setTranType(String tranType) { this.tranType = tranType; }

    public String getTranTypeDesc() { return tranTypeDesc; }
    public void setTranTypeDesc(String tranTypeDesc) { this.tranTypeDesc = tranTypeDesc; }
}
