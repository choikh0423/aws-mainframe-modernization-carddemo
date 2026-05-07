package com.carddemo.transaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * JPA entity mapping to CVACT01Y ACCOUNT-RECORD (300 bytes).
 * Included for schema completeness only. COTRN02C includes this copybook
 * but does NOT perform any EXEC CICS I/O against the account file.
 * This entity is intentionally not wired into the transaction-add flow
 * to preserve 1:1 parity with the COBOL original.
 */
@Entity
@Table(name = "account_record")
public class AccountRecord {

    @Id
    @Column(name = "acct_id", nullable = false)
    private long acctId;

    @Column(name = "acct_active_status", length = 1)
    private String acctActiveStatus;

    @Column(name = "acct_curr_bal", precision = 12, scale = 2)
    private BigDecimal acctCurrBal;

    @Column(name = "acct_credit_limit", precision = 12, scale = 2)
    private BigDecimal acctCreditLimit;

    @Column(name = "acct_cash_credit_limit", precision = 12, scale = 2)
    private BigDecimal acctCashCreditLimit;

    @Column(name = "acct_open_date", length = 10)
    private String acctOpenDate;

    @Column(name = "acct_expiration_date", length = 10)
    private String acctExpirationDate;

    @Column(name = "acct_reissue_date", length = 10)
    private String acctReissueDate;

    @Column(name = "acct_curr_cyc_credit", precision = 12, scale = 2)
    private BigDecimal acctCurrCycCredit;

    @Column(name = "acct_curr_cyc_debit", precision = 12, scale = 2)
    private BigDecimal acctCurrCycDebit;

    @Column(name = "acct_addr_zip", length = 10)
    private String acctAddrZip;

    @Column(name = "acct_group_id", length = 10)
    private String acctGroupId;

    public AccountRecord() {
    }

    public long getAcctId() { return acctId; }
    public void setAcctId(long acctId) { this.acctId = acctId; }
    public String getAcctActiveStatus() { return acctActiveStatus; }
    public void setAcctActiveStatus(String s) { this.acctActiveStatus = s; }
    public BigDecimal getAcctCurrBal() { return acctCurrBal; }
    public void setAcctCurrBal(BigDecimal v) { this.acctCurrBal = v; }
    public BigDecimal getAcctCreditLimit() { return acctCreditLimit; }
    public void setAcctCreditLimit(BigDecimal v) { this.acctCreditLimit = v; }
    public BigDecimal getAcctCashCreditLimit() { return acctCashCreditLimit; }
    public void setAcctCashCreditLimit(BigDecimal v) { this.acctCashCreditLimit = v; }
    public String getAcctOpenDate() { return acctOpenDate; }
    public void setAcctOpenDate(String s) { this.acctOpenDate = s; }
    public String getAcctExpirationDate() { return acctExpirationDate; }
    public void setAcctExpirationDate(String s) { this.acctExpirationDate = s; }
    public String getAcctReissueDate() { return acctReissueDate; }
    public void setAcctReissueDate(String s) { this.acctReissueDate = s; }
    public BigDecimal getAcctCurrCycCredit() { return acctCurrCycCredit; }
    public void setAcctCurrCycCredit(BigDecimal v) { this.acctCurrCycCredit = v; }
    public BigDecimal getAcctCurrCycDebit() { return acctCurrCycDebit; }
    public void setAcctCurrCycDebit(BigDecimal v) { this.acctCurrCycDebit = v; }
    public String getAcctAddrZip() { return acctAddrZip; }
    public void setAcctAddrZip(String s) { this.acctAddrZip = s; }
    public String getAcctGroupId() { return acctGroupId; }
    public void setAcctGroupId(String s) { this.acctGroupId = s; }
}
