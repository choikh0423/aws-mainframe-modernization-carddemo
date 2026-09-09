package com.carddemo.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/** Composite primary key of {@link AuthFraudRecord}. */
@Embeddable
public class AuthFraudId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "card_num", length = 16, nullable = false)
    private String cardNum;

    @Column(name = "auth_ts", nullable = false)
    private LocalDateTime authTs;

    public AuthFraudId() {
    }

    public AuthFraudId(String cardNum, LocalDateTime authTs) {
        this.cardNum = cardNum;
        this.authTs = authTs;
    }

    public String getCardNum() { return cardNum; }
    public void setCardNum(String cardNum) { this.cardNum = cardNum; }

    public LocalDateTime getAuthTs() { return authTs; }
    public void setAuthTs(LocalDateTime authTs) { this.authTs = authTs; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AuthFraudId other)) {
            return false;
        }
        return Objects.equals(cardNum, other.cardNum)
                && Objects.equals(authTs, other.authTs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cardNum, authTs);
    }
}
