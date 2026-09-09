package com.carddemo.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * IMS root segment PAUTSUM0 of DBPAUTP0 (copybook CIPAUSMY), flattened to a table.
 * The DBPAUTX0 secondary index PAUTINDX is on ACCNTID, which is this table's
 * primary key, so it needs no separate structure.
 */
@Entity
@Table(name = "pending_auth_summary")
public class PendingAuthSummaryRecord {

    @Id
    @Column(name = "pa_acct_id", nullable = false)
    private Long paAcctId;

    @Column(name = "pa_cust_id", nullable = false)
    private Long paCustId;

    @Column(name = "pa_auth_status", length = 1, nullable = false)
    private String paAuthStatus;

    @Column(name = "pa_account_status_1", length = 2)
    private String paAccountStatus1;

    @Column(name = "pa_account_status_2", length = 2)
    private String paAccountStatus2;

    @Column(name = "pa_account_status_3", length = 2)
    private String paAccountStatus3;

    @Column(name = "pa_account_status_4", length = 2)
    private String paAccountStatus4;

    @Column(name = "pa_account_status_5", length = 2)
    private String paAccountStatus5;

    @Column(name = "pa_credit_limit", precision = 11, scale = 2, nullable = false)
    private BigDecimal paCreditLimit;

    @Column(name = "pa_cash_limit", precision = 11, scale = 2, nullable = false)
    private BigDecimal paCashLimit;

    @Column(name = "pa_credit_balance", precision = 11, scale = 2, nullable = false)
    private BigDecimal paCreditBalance;

    @Column(name = "pa_cash_balance", precision = 11, scale = 2, nullable = false)
    private BigDecimal paCashBalance;

    @Column(name = "pa_approved_auth_cnt", nullable = false)
    private Integer paApprovedAuthCnt;

    @Column(name = "pa_declined_auth_cnt", nullable = false)
    private Integer paDeclinedAuthCnt;

    @Column(name = "pa_approved_auth_amt", precision = 11, scale = 2, nullable = false)
    private BigDecimal paApprovedAuthAmt;

    @Column(name = "pa_declined_auth_amt", precision = 11, scale = 2, nullable = false)
    private BigDecimal paDeclinedAuthAmt;

    public PendingAuthSummaryRecord() {
    }

    public Long getPaAcctId() { return paAcctId; }
    public void setPaAcctId(Long paAcctId) { this.paAcctId = paAcctId; }

    public Long getPaCustId() { return paCustId; }
    public void setPaCustId(Long paCustId) { this.paCustId = paCustId; }

    public String getPaAuthStatus() { return paAuthStatus; }
    public void setPaAuthStatus(String paAuthStatus) { this.paAuthStatus = paAuthStatus; }

    public String getPaAccountStatus1() { return paAccountStatus1; }
    public void setPaAccountStatus1(String paAccountStatus1) { this.paAccountStatus1 = paAccountStatus1; }

    public String getPaAccountStatus2() { return paAccountStatus2; }
    public void setPaAccountStatus2(String paAccountStatus2) { this.paAccountStatus2 = paAccountStatus2; }

    public String getPaAccountStatus3() { return paAccountStatus3; }
    public void setPaAccountStatus3(String paAccountStatus3) { this.paAccountStatus3 = paAccountStatus3; }

    public String getPaAccountStatus4() { return paAccountStatus4; }
    public void setPaAccountStatus4(String paAccountStatus4) { this.paAccountStatus4 = paAccountStatus4; }

    public String getPaAccountStatus5() { return paAccountStatus5; }
    public void setPaAccountStatus5(String paAccountStatus5) { this.paAccountStatus5 = paAccountStatus5; }

    public BigDecimal getPaCreditLimit() { return paCreditLimit; }
    public void setPaCreditLimit(BigDecimal paCreditLimit) { this.paCreditLimit = paCreditLimit; }

    public BigDecimal getPaCashLimit() { return paCashLimit; }
    public void setPaCashLimit(BigDecimal paCashLimit) { this.paCashLimit = paCashLimit; }

    public BigDecimal getPaCreditBalance() { return paCreditBalance; }
    public void setPaCreditBalance(BigDecimal paCreditBalance) { this.paCreditBalance = paCreditBalance; }

    public BigDecimal getPaCashBalance() { return paCashBalance; }
    public void setPaCashBalance(BigDecimal paCashBalance) { this.paCashBalance = paCashBalance; }

    public Integer getPaApprovedAuthCnt() { return paApprovedAuthCnt; }
    public void setPaApprovedAuthCnt(Integer paApprovedAuthCnt) { this.paApprovedAuthCnt = paApprovedAuthCnt; }

    public Integer getPaDeclinedAuthCnt() { return paDeclinedAuthCnt; }
    public void setPaDeclinedAuthCnt(Integer paDeclinedAuthCnt) { this.paDeclinedAuthCnt = paDeclinedAuthCnt; }

    public BigDecimal getPaApprovedAuthAmt() { return paApprovedAuthAmt; }
    public void setPaApprovedAuthAmt(BigDecimal paApprovedAuthAmt) { this.paApprovedAuthAmt = paApprovedAuthAmt; }

    public BigDecimal getPaDeclinedAuthAmt() { return paDeclinedAuthAmt; }
    public void setPaDeclinedAuthAmt(BigDecimal paDeclinedAuthAmt) { this.paDeclinedAuthAmt = paDeclinedAuthAmt; }
}
