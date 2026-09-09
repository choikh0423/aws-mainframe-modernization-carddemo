package com.carddemo.billpayment.dto;

/**
 * The two unprotected fields of map COBIL0A (app/bms/COBIL00.bms:85, 115) as the
 * operator typed them: {@code ACTIDIN} (11) and {@code CONFIRM} (1). Both are
 * raw strings — COBIL00C applies no numeric edit to either (quirk Q-4).
 */
public class BillPaymentRequest {

    private String accountId;
    private String confirm;

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getConfirm() { return confirm; }
    public void setConfirm(String confirm) { this.confirm = confirm; }
}
