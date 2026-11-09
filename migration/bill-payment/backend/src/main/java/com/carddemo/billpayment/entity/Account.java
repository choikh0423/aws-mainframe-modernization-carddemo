package com.carddemo.billpayment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** ACCTDAT record (CVACT01Y ACCOUNT-RECORD). */
@Entity
@Table(name = "account")
public class Account {
    @Id
    @Column(name = "acct_id", length = 11)
    private String acctId;
    private String activeStatus;
    private BigDecimal currBal;
    private BigDecimal creditLimit;
    private BigDecimal cashCreditLimit;
    private String openDate;
    private String expirationDate;
    private String reissueDate;
    private BigDecimal currCycCredit;
    private BigDecimal currCycDebit;
    private String addrZip;
    private String groupId;

    public String getAcctId() { return acctId; }
    public void setAcctId(String v) { acctId = v; }
    public String getActiveStatus() { return activeStatus; }
    public void setActiveStatus(String v) { activeStatus = v; }
    public BigDecimal getCurrBal() { return currBal; }
    public void setCurrBal(BigDecimal v) { currBal = v; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal v) { creditLimit = v; }
    public BigDecimal getCashCreditLimit() { return cashCreditLimit; }
    public void setCashCreditLimit(BigDecimal v) { cashCreditLimit = v; }
    public String getOpenDate() { return openDate; }
    public void setOpenDate(String v) { openDate = v; }
    public String getExpirationDate() { return expirationDate; }
    public void setExpirationDate(String v) { expirationDate = v; }
    public String getReissueDate() { return reissueDate; }
    public void setReissueDate(String v) { reissueDate = v; }
    public BigDecimal getCurrCycCredit() { return currCycCredit; }
    public void setCurrCycCredit(BigDecimal v) { currCycCredit = v; }
    public BigDecimal getCurrCycDebit() { return currCycDebit; }
    public void setCurrCycDebit(BigDecimal v) { currCycDebit = v; }
    public String getAddrZip() { return addrZip; }
    public void setAddrZip(String v) { addrZip = v; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String v) { groupId = v; }
}
