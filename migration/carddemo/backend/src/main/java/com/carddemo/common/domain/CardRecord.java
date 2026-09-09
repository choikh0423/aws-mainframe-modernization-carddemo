package com.carddemo.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;


/**
 * CARDDAT VSAM KSDS record CVACT02Y CARD-RECORD (RECLN 150). CARDAIX (card by
 * account) is served by {@code idx_cards_acct}.
 */
@Entity
@Table(name = "cards", indexes = @Index(name = "idx_cards_acct", columnList = "acct_id"))
public class CardRecord {

    @Id
    @Column(name = "card_num", length = 16, nullable = false)
    private String cardNum;

    @Column(name = "acct_id", nullable = false)
    private Long acctId;

    @Column(name = "cvv_cd", nullable = false)
    private Integer cvvCd;

    @Column(name = "embossed_name", length = 50, nullable = false)
    private String embossedName;

    @Column(name = "expiraion_date", length = 10, nullable = false)
    private String expiraionDate;

    @Column(name = "active_status", length = 1, nullable = false)
    private String activeStatus;

    public CardRecord() {
    }

    public String getCardNum() { return cardNum; }
    public void setCardNum(String cardNum) { this.cardNum = cardNum; }

    public Long getAcctId() { return acctId; }
    public void setAcctId(Long acctId) { this.acctId = acctId; }

    public Integer getCvvCd() { return cvvCd; }
    public void setCvvCd(Integer cvvCd) { this.cvvCd = cvvCd; }

    public String getEmbossedName() { return embossedName; }
    public void setEmbossedName(String embossedName) { this.embossedName = embossedName; }

    public String getExpiraionDate() { return expiraionDate; }
    public void setExpiraionDate(String expiraionDate) { this.expiraionDate = expiraionDate; }

    public String getActiveStatus() { return activeStatus; }
    public void setActiveStatus(String activeStatus) { this.activeStatus = activeStatus; }
}
