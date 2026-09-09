package com.carddemo.trantype.dto;

/**
 * One of the seven fetched rows of the CTLI map ({@code WS-CA-EACH-ROW-OUT},
 * COTRTLIC.cbl:390-392): the type code and the description as read from
 * TRANSACTION_TYPE.
 *
 * @param typeCode    TR_TYPE, two characters
 * @param description TR_DESCRIPTION, trailing spaces stripped for transport
 */
public record TranTypeListRow(String typeCode, String description) {

    /** {@code MOVE LOW-VALUES TO WS-CA-EACH-ROW-OUT}: an unused screen line. */
    public static TranTypeListRow empty() {
        return new TranTypeListRow("", "");
    }

    public boolean isEmpty() {
        return typeCode == null || typeCode.isBlank();
    }
}
