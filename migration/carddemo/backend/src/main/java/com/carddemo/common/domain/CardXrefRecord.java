package com.carddemo.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;


/**
 * CCXREF VSAM KSDS record CVACT03Y CARD-XREF-RECORD (RECLN 50).
 * Primary key = XREF-CARD-NUM (CCXREF base cluster). {@code acctId} is indexed to
 * replace the CXACAIX alternate index used to resolve a card from an account.
 */
@Entity
@Table(name = "card_xref", indexes = @Index(name = "idx_card_xref_acct", columnList = "acct_id"))
public class CardXrefRecord {

    @Id
    @Column(name = "card_num", length = 16, nullable = false)
    private String cardNum;

    @Column(name = "cust_id", nullable = false)
    private Long custId;

    @Column(name = "acct_id", nullable = false)
    private Long acctId;

    public CardXrefRecord() {
    }

    public String getCardNum() { return cardNum; }
    public void setCardNum(String cardNum) { this.cardNum = cardNum; }

    public Long getCustId() { return custId; }
    public void setCustId(Long custId) { this.custId = custId; }

    public Long getAcctId() { return acctId; }
    public void setAcctId(Long acctId) { this.acctId = acctId; }
}
