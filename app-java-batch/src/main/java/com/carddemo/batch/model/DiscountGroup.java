package com.carddemo.batch.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * JPA entity for the Disclosure/Discount Group record.
 * Migrated from COBOL copybook CVTRA02Y.cpy (RECLN 50).
 * Composite key: (acctGroupId, tranTypeCd, tranCatCd).
 */
@Entity
@Table(name = "discount_group")
@IdClass(DiscountGroupId.class)
public class DiscountGroup {

    @Id
    @Column(name = "acct_group_id", length = 10)
    private String acctGroupId;

    @Id
    @Column(name = "tran_type_cd", length = 2)
    private String tranTypeCd;

    @Id
    @Column(name = "tran_cat_cd")
    private Integer tranCatCd;

    @Column(name = "int_rate", precision = 6, scale = 2)
    private BigDecimal intRate;

    public DiscountGroup() {
    }

    public String getAcctGroupId() {
        return acctGroupId;
    }

    public void setAcctGroupId(String acctGroupId) {
        this.acctGroupId = acctGroupId;
    }

    public String getTranTypeCd() {
        return tranTypeCd;
    }

    public void setTranTypeCd(String tranTypeCd) {
        this.tranTypeCd = tranTypeCd;
    }

    public Integer getTranCatCd() {
        return tranCatCd;
    }

    public void setTranCatCd(Integer tranCatCd) {
        this.tranCatCd = tranCatCd;
    }

    public BigDecimal getIntRate() {
        return intRate;
    }

    public void setIntRate(BigDecimal intRate) {
        this.intRate = intRate;
    }
}
