package com.carddemo.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * CARD-XREF-RECORD (copybook CVACT03Y, RECLN 50). Backs both the CCXREF file
 * (keyed by card number) and its CXACAIX alternate index (keyed by account id).
 */
@Entity
@Table(name = "ccxref")
public class CardXref {

    public static final int XREF_CARD_NUM_LEN = 16;
    public static final int XREF_ACCT_ID_LEN = 11;

    @Id
    @Column(name = "xref_card_num", length = XREF_CARD_NUM_LEN, nullable = false)
    private String xrefCardNum;

    @Column(name = "xref_cust_id")
    private Long xrefCustId;

    @Column(name = "xref_acct_id", length = XREF_ACCT_ID_LEN)
    private String xrefAcctId;

    public String getXrefCardNum() {
        return xrefCardNum;
    }

    public void setXrefCardNum(String xrefCardNum) {
        this.xrefCardNum = xrefCardNum;
    }

    public Long getXrefCustId() {
        return xrefCustId;
    }

    public void setXrefCustId(Long xrefCustId) {
        this.xrefCustId = xrefCustId;
    }

    public String getXrefAcctId() {
        return xrefAcctId;
    }

    public void setXrefAcctId(String xrefAcctId) {
        this.xrefAcctId = xrefAcctId;
    }
}
