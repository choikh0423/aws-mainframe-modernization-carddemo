package com.carddemo.trantype.dto;

/**
 * One CTTU keystroke.
 *
 * @param aid         ENTER, PF3, PF4, PF5 or PF12; anything else is reported
 *                    as "Invalid key pressed" (COTRTUPC.cbl:577-608)
 * @param typeCode    {@code TRTYPCDI}
 * @param description {@code TRTYDSCI}
 * @param state       the COMMAREA; {@code null} on the first entry
 */
public record TranTypeUpdateRequest(String aid,
                                    String typeCode,
                                    String description,
                                    TranTypeUpdateState state) {
}
