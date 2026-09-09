package com.carddemo.reporting.dto;

/**
 * A CR00 error send: the ERRMSG text, the field CORPT00C put the cursor on
 * ({@code MOVE -1 TO <field>L}) and, once NUMVAL-C has run, the normalised date
 * fields the map would be redisplaying.
 */
public class ReportScreenErrorResponse {

    private final String message;
    private final String cursor;
    private final CustomDateFields dateFields;

    public ReportScreenErrorResponse(String message, String cursor, CustomDateFields dateFields) {
        this.message = message;
        this.cursor = cursor;
        this.dateFields = dateFields;
    }

    public String getMessage() { return message; }
    public String getCursor() { return cursor; }
    public CustomDateFields getDateFields() { return dateFields; }
}
