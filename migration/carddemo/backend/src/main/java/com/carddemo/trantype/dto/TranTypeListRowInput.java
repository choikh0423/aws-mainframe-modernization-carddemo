package com.carddemo.trantype.dto;

/**
 * The two unprotected fields of one CTLI screen line
 * ({@code WS-EDIT-SELECT(I)} and {@code WS-ROW-TR-DESC-IN(I)}).
 *
 * @param selection  the action flag: blank, {@code U} or {@code D}; the
 *                   comparison is on the upper-cased value
 * @param description the (possibly edited) description text
 */
public record TranTypeListRowInput(String selection, String description) {

    public static TranTypeListRowInput blank() {
        return new TranTypeListRowInput("", "");
    }

    /** The flag as the COBOL sees it: upper case, spaces for LOW-VALUES. */
    public String flag() {
        return selection == null ? "" : selection.trim().toUpperCase();
    }
}
