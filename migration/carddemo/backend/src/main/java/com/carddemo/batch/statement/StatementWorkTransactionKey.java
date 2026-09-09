package com.carddemo.batch.statement;

import java.io.Serializable;
import java.util.Objects;

/**
 * TRNX-KEY (app/cpy/COSTM01.CPY:22-24): the card number and transaction id pair
 * defined as KEYS(32 0) on the work KSDS (CREASTMT.JCL:36).
 */
public class StatementWorkTransactionKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String cardNum;

    private String tranId;

    public StatementWorkTransactionKey() {
    }

    public StatementWorkTransactionKey(String cardNum, String tranId) {
        this.cardNum = cardNum;
        this.tranId = tranId;
    }

    public String getCardNum() {
        return cardNum;
    }

    public void setCardNum(String cardNum) {
        this.cardNum = cardNum;
    }

    public String getTranId() {
        return tranId;
    }

    public void setTranId(String tranId) {
        this.tranId = tranId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StatementWorkTransactionKey key)) {
            return false;
        }
        return Objects.equals(cardNum, key.cardNum) && Objects.equals(tranId, key.tranId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cardNum, tranId);
    }
}
