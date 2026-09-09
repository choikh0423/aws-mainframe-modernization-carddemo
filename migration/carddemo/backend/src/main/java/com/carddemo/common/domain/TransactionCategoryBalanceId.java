package com.carddemo.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Composite primary key of {@link TransactionCategoryBalanceRecord}. */
@Embeddable
public class TransactionCategoryBalanceId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "acct_id", nullable = false)
    private Long acctId;

    @Column(name = "type_cd", length = 2, nullable = false)
    private String typeCd;

    @Column(name = "cat_cd", nullable = false)
    private Integer catCd;

    public TransactionCategoryBalanceId() {
    }

    public TransactionCategoryBalanceId(Long acctId, String typeCd, Integer catCd) {
        this.acctId = acctId;
        this.typeCd = typeCd;
        this.catCd = catCd;
    }

    public Long getAcctId() { return acctId; }
    public void setAcctId(Long acctId) { this.acctId = acctId; }

    public String getTypeCd() { return typeCd; }
    public void setTypeCd(String typeCd) { this.typeCd = typeCd; }

    public Integer getCatCd() { return catCd; }
    public void setCatCd(Integer catCd) { this.catCd = catCd; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransactionCategoryBalanceId other)) {
            return false;
        }
        return Objects.equals(acctId, other.acctId)
                && Objects.equals(typeCd, other.typeCd)
                && Objects.equals(catCd, other.catCd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(acctId, typeCd, catCd);
    }
}
