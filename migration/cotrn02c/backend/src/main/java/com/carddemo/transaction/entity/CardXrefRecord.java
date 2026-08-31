package com.carddemo.transaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity mapping to CVACT03Y CARD-XREF-RECORD (50 bytes).
 * Primary key = XREF-CARD-NUM (CCXREF VSAM base cluster).
 * XREF-ACCT-ID has an index (replaces CXACAIX alternate index).
 */
@Entity
@Table(name = "card_xref")
public class CardXrefRecord {

    @Id
    @Column(name = "xref_card_num", length = 16, nullable = false)
    private String xrefCardNum;

    @Column(name = "xref_cust_id", nullable = false)
    private long xrefCustId;

    @Column(name = "xref_acct_id", nullable = false)
    private long xrefAcctId;

    public CardXrefRecord() {
    }

    public String getXrefCardNum() { return xrefCardNum; }
    public void setXrefCardNum(String xrefCardNum) { this.xrefCardNum = xrefCardNum; }

    public long getXrefCustId() { return xrefCustId; }
    public void setXrefCustId(long xrefCustId) { this.xrefCustId = xrefCustId; }

    public long getXrefAcctId() { return xrefAcctId; }
    public void setXrefAcctId(long xrefAcctId) { this.xrefAcctId = xrefAcctId; }
}
