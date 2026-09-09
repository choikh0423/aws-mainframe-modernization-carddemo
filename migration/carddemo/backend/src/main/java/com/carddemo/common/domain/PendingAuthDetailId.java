package com.carddemo.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Composite primary key of {@link PendingAuthDetailRecord}. */
@Embeddable
public class PendingAuthDetailId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "pa_acct_id", nullable = false)
    private Long paAcctId;

    @Column(name = "pa_auth_date_9c", nullable = false)
    private Integer paAuthDate9c;

    @Column(name = "pa_auth_time_9c", nullable = false)
    private Long paAuthTime9c;

    public PendingAuthDetailId() {
    }

    public PendingAuthDetailId(Long paAcctId, Integer paAuthDate9c, Long paAuthTime9c) {
        this.paAcctId = paAcctId;
        this.paAuthDate9c = paAuthDate9c;
        this.paAuthTime9c = paAuthTime9c;
    }

    public Long getPaAcctId() { return paAcctId; }
    public void setPaAcctId(Long paAcctId) { this.paAcctId = paAcctId; }

    public Integer getPaAuthDate9c() { return paAuthDate9c; }
    public void setPaAuthDate9c(Integer paAuthDate9c) { this.paAuthDate9c = paAuthDate9c; }

    public Long getPaAuthTime9c() { return paAuthTime9c; }
    public void setPaAuthTime9c(Long paAuthTime9c) { this.paAuthTime9c = paAuthTime9c; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PendingAuthDetailId other)) {
            return false;
        }
        return Objects.equals(paAcctId, other.paAcctId)
                && Objects.equals(paAuthDate9c, other.paAuthDate9c)
                && Objects.equals(paAuthTime9c, other.paAuthTime9c);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paAcctId, paAuthDate9c, paAuthTime9c);
    }
}
