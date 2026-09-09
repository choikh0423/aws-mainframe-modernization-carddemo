package com.carddemo.reporting;

/**
 * The map CORPT0A field names CORPT00C positions the cursor on with
 * {@code MOVE -1 TO <field>L}. They travel to the React screen as the
 * {@code cursor} of a response so it can focus the same input the 3270 did.
 */
public final class ScreenField {

    private ScreenField() {
    }

    public static final String MONTHLY = "MONTHLY";
    public static final String SDTMM = "SDTMM";
    public static final String SDTDD = "SDTDD";
    public static final String SDTYYYY = "SDTYYYY";
    public static final String EDTMM = "EDTMM";
    public static final String EDTDD = "EDTDD";
    public static final String EDTYYYY = "EDTYYYY";
    public static final String CONFIRM = "CONFIRM";
}
