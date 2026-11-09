package com.carddemo.billpayment.dto;

/**
 * One AID key press on the Bill Payment screen: the key plus the screen
 * fields as the terminal returns them (COBIL0AI ACTIDIN / CURBAL / CONFIRM)
 * and the COMMAREA field the program uses for PF3 (CDEMO-FROM-PROGRAM).
 */
public record BillPaymentRequest(String aid, String acctId, String currentBalance,
                                 String confirm, String fromProgram) {
}
