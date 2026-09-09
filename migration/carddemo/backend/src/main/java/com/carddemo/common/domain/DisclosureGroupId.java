package com.carddemo.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Composite primary key of {@link DisclosureGroupRecord}. */
@Embeddable
public class DisclosureGroupId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "acct_group_id", length = 10, nullable = false)
    private String acctGroupId;

    @Column(name = "tran_type_cd", length = 2, nullable = false)
    private String tranTypeCd;

    @Column(name = "tran_cat_cd", nullable = false)
    private Integer tranCatCd;

    public DisclosureGroupId() {
    }

    public DisclosureGroupId(String acctGroupId, String tranTypeCd, Integer tranCatCd) {
        this.acctGroupId = acctGroupId;
        this.tranTypeCd = tranTypeCd;
        this.tranCatCd = tranCatCd;
    }

    public String getAcctGroupId() { return acctGroupId; }
    public void setAcctGroupId(String acctGroupId) { this.acctGroupId = acctGroupId; }

    public String getTranTypeCd() { return tranTypeCd; }
    public void setTranTypeCd(String tranTypeCd) { this.tranTypeCd = tranTypeCd; }

    public Integer getTranCatCd() { return tranCatCd; }
    public void setTranCatCd(Integer tranCatCd) { this.tranCatCd = tranCatCd; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DisclosureGroupId other)) {
            return false;
        }
        return Objects.equals(acctGroupId, other.acctGroupId)
                && Objects.equals(tranTypeCd, other.tranTypeCd)
                && Objects.equals(tranCatCd, other.tranCatCd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(acctGroupId, tranTypeCd, tranCatCd);
    }
}
