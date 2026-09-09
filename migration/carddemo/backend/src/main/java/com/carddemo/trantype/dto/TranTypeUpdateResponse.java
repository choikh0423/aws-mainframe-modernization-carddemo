package com.carddemo.trantype.dto;

/**
 * The CTTU map as sent back to the terminal ({@code 3000-SEND-MAP}).
 *
 * @param typeCode            {@code TRTYPCDO}
 * @param description         {@code TRTYDSCO}
 * @param infoMessage         {@code INFOMSGO}
 * @param errorMessage        {@code ERRMSGO}
 * @param typeCodeEditable    {@code TRTYPCDA} is unprotected
 *                            ({@code 3300-SETUP-SCREEN-ATTRS})
 * @param descriptionEditable {@code TRTYDSCA} is unprotected
 * @param enterEnabled        the ENTER prompt is highlighted
 *                            ({@code 3391-SETUP-PFKEY-ATTRS})
 * @param f4Enabled           the F4=Delete prompt is highlighted
 * @param f5Enabled           the F5=Save prompt is highlighted
 * @param f12Enabled          the F12=Cancel prompt is highlighted
 * @param nextProgram         {@code CCARD-NEXT-PROG} / {@code CDEMO-TO-PROGRAM}
 * @param nextTranId          the transaction id that goes with it
 * @param state               the COMMAREA for the next keystroke
 */
public record TranTypeUpdateResponse(String typeCode,
                                     String description,
                                     String infoMessage,
                                     String errorMessage,
                                     boolean typeCodeEditable,
                                     boolean descriptionEditable,
                                     boolean enterEnabled,
                                     boolean f4Enabled,
                                     boolean f5Enabled,
                                     boolean f12Enabled,
                                     String nextProgram,
                                     String nextTranId,
                                     TranTypeUpdateState state) {
}
