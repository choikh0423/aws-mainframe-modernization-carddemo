package com.carddemo.batch.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the Card Cross-Reference record.
 * Migrated from COBOL copybook CVACT03Y.cpy (RECLN 50).
 * VSAM KSDS key: XREF-CARD-NUM PIC X(16).
 */
@Entity
@Table(name = "card_xref")
public class CardXref {

    @Id
    @Column(name = "card_num", length = 16)
    private String cardNum;

    @Column(name = "cust_id")
    private Long custId;

    @Column(name = "acct_id")
    private Long acctId;

    public CardXref() {
    }

    public String getCardNum() {
        return cardNum;
    }

    public void setCardNum(String cardNum) {
        this.cardNum = cardNum;
    }

    public Long getCustId() {
        return custId;
    }

    public void setCustId(Long custId) {
        this.custId = custId;
    }

    public Long getAcctId() {
        return acctId;
    }

    public void setAcctId(Long acctId) {
        this.acctId = acctId;
    }
}
