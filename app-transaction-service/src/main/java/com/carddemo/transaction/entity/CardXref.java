package com.carddemo.transaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * CCXREF record, copybook CVACT03Y (CARD-XREF-RECORD, RECLN 50).
 * The acctId index stands in for the CXACAIX alternate index.
 */
@Entity
@Table(name = "ccxref", indexes = @Index(name = "cxacaix", columnList = "xref_acct_id"))
public class CardXref {

    @Id
    @Column(name = "xref_card_num", length = 16, nullable = false)
    private String cardNum;

    @Column(name = "xref_cust_id")
    private Long custId;

    @Column(name = "xref_acct_id")
    private Long acctId;

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
