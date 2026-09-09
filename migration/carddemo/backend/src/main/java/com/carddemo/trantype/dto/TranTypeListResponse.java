package com.carddemo.trantype.dto;

import java.util.List;

/**
 * The CTLI map as sent back to the terminal ({@code 2000-SEND-MAP}).
 *
 * @param typeFilter        the value left in {@code TRTYPEO}
 * @param descFilter        the value left in {@code TRDESCO}
 * @param rows              the seven display lines, always seven entries
 * @param pageNumber        {@code PAGENUMO}, {@code WS-CA-SCREEN-NUM}
 * @param infoMessage       {@code INFOMSGO}, the centred info line
 * @param errorMessage      {@code ERRMSGO}, the red error line
 * @param protectSelectRows {@code FLG-PROTECT-SELECT-ROWS}: the action column
 *                          is protected while a filter is in error
 * @param nextProgram       {@code CCARD-NEXT-PROG}: COTRTLIC to stay on this
 *                          screen, COADM01C on F3, COTRTUPC on F2
 * @param nextTranId        the transaction id that goes with {@code nextProgram}
 * @param state             the COMMAREA to send back with the next keystroke
 */
public record TranTypeListResponse(String typeFilter,
                                   String descFilter,
                                   List<TranTypeListDisplayRow> rows,
                                   int pageNumber,
                                   String infoMessage,
                                   String errorMessage,
                                   boolean protectSelectRows,
                                   String nextProgram,
                                   String nextTranId,
                                   TranTypeListState state) {
}
