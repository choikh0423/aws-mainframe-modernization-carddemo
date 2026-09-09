package com.carddemo.trantype.dto;

/**
 * One rendered CTLI screen line ({@code 2300-SCREEN-ARRAY-INIT}).
 *
 * @param typeCode    {@code TRTTYPO}
 * @param description {@code TRTYPDO}: the fetched value, or the operator's own
 *                    text while an update is pending, or {@code *} when the
 *                    operator blanked it (COTRTLIC.cbl:1412-1422)
 * @param selection   {@code TRTSELO}: the action flag echoed back, blanked
 *                    once the action completed
 * @param highlighted the row the pending action applies to; the BMS map turns
 *                    it red/reverse while awaiting F10
 * @param inError     the row carries an invalid action code
 */
public record TranTypeListDisplayRow(String typeCode,
                                     String description,
                                     String selection,
                                     boolean highlighted,
                                     boolean inError) {
}
