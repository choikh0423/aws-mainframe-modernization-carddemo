package com.carddemo.billpayment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** CCXREF record (CVACT03Y CARD-XREF-RECORD); CXACAIX is the acct_id index. */
@Entity
@Table(name = "card_xref")
public class CardXref {
    @Id
    @Column(name = "card_num", length = 16)
    private String cardNum;
    private Long custId;
    @Column(name = "acct_id", length = 11)
    private String acctId;

    public String getCardNum() { return cardNum; }
    public void setCardNum(String v) { cardNum = v; }
    public Long getCustId() { return custId; }
    public void setCustId(Long v) { custId = v; }
    public String getAcctId() { return acctId; }
    public void setAcctId(String v) { acctId = v; }
}
