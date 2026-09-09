package com.carddemo.trantype.dto;

import java.util.List;

/**
 * One CTLI keystroke: the AID key, the two filter fields and the seven screen
 * lines as they came back from the terminal, plus the COMMAREA the previous
 * turn returned.
 *
 * @param aid        ENTER, PF2, PF3, PF7, PF8 or PF10; anything else is
 *                   treated as ENTER (COTRTLIC.cbl:565-580)
 * @param typeFilter {@code TRTYPEI}, the type-code filter
 * @param descFilter {@code TRDESCI}, the description filter
 * @param rows       the seven {@code TRTSELI}/{@code TRTYPDI} pairs; a shorter
 *                   list is padded with blanks
 * @param state      the COMMAREA; {@code null} on the first entry
 *                   ({@code EIBCALEN = 0})
 */
public record TranTypeListRequest(String aid,
                                  String typeFilter,
                                  String descFilter,
                                  List<TranTypeListRowInput> rows,
                                  TranTypeListState state) {
}
