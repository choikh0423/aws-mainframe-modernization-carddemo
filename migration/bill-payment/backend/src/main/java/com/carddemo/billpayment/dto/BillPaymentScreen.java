package com.carddemo.billpayment.dto;

/**
 * What SEND MAP('COBIL0A') would put on the screen.  {@code cursor} is the
 * field that gets the cursor (ACTIDIN or CONFIRM); {@code nextProgram} is
 * set when the program transfers control (PF3 -> XCTL).
 */
public record BillPaymentScreen(String tranId, String programName, String currentDate,
                                String currentTime, String acctId, String currentBalance,
                                String confirm, String message, String messageColor,
                                String cursor, String nextProgram, String transactionId) {
}
