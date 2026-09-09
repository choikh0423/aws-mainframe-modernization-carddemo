package com.carddemo.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Composite primary key of {@link Db2TransactionTypeCategoryRecord}. */
@Embeddable
public class Db2TransactionTypeCategoryId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "trc_type_code", length = 2, nullable = false)
    private String trcTypeCode;

    @Column(name = "trc_type_category", length = 4, nullable = false)
    private String trcTypeCategory;

    public Db2TransactionTypeCategoryId() {
    }

    public Db2TransactionTypeCategoryId(String trcTypeCode, String trcTypeCategory) {
        this.trcTypeCode = trcTypeCode;
        this.trcTypeCategory = trcTypeCategory;
    }

    public String getTrcTypeCode() { return trcTypeCode; }
    public void setTrcTypeCode(String trcTypeCode) { this.trcTypeCode = trcTypeCode; }

    public String getTrcTypeCategory() { return trcTypeCategory; }
    public void setTrcTypeCategory(String trcTypeCategory) { this.trcTypeCategory = trcTypeCategory; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Db2TransactionTypeCategoryId other)) {
            return false;
        }
        return Objects.equals(trcTypeCode, other.trcTypeCode)
                && Objects.equals(trcTypeCategory, other.trcTypeCategory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trcTypeCode, trcTypeCategory);
    }
}
