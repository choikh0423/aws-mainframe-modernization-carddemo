package com.carddemo.batch.statement;

/**
 * WS-M03B-AREA / LK-M03B-AREA, the parameter CBSTM03A passes to CBSTM03B
 * (CBSTM03A.CBL:71-83, CBSTM03B.CBL:100-112).
 */
public class StatementFileArea {

    /** LK-M03B-DD values (CBSTM03B.CBL:118-126). */
    public static final String DD_TRNXFILE = "TRNXFILE";
    public static final String DD_XREFFILE = "XREFFILE";
    public static final String DD_CUSTFILE = "CUSTFILE";
    public static final String DD_ACCTFILE = "ACCTFILE";

    /** LK-M03B-OPER values (CBSTM03B.CBL:103-108). */
    public static final char OPER_OPEN = 'O';
    public static final char OPER_CLOSE = 'C';
    public static final char OPER_READ = 'R';
    public static final char OPER_READ_KEY = 'K';
    public static final char OPER_WRITE = 'W';
    public static final char OPER_REWRITE = 'Z';

    /** FILE STATUS values the statement stream evaluates. */
    public static final String RC_OK = "00";
    public static final String RC_END_OF_FILE = "10";
    public static final String RC_NOT_FOUND = "23";
    public static final String RC_OPEN_FAILED = "35";
    public static final String RC_ERROR = "90";

    private String dd;
    private char oper;
    private String rc = "  ";
    private String key = "";
    private int keyLength;
    private String data = "";

    public String getDd() {
        return dd;
    }

    public void setDd(String dd) {
        this.dd = dd;
    }

    public char getOper() {
        return oper;
    }

    public void setOper(char oper) {
        this.oper = oper;
    }

    public String getRc() {
        return rc;
    }

    public void setRc(String rc) {
        this.rc = rc;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public int getKeyLength() {
        return keyLength;
    }

    public void setKeyLength(int keyLength) {
        this.keyLength = keyLength;
    }

    /** LK-M03B-FLDT: the record the read returned. */
    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    /** LK-M03B-KEY (1:LK-M03B-KEY-LN), the key value the keyed read uses. */
    String keyValue() {
        return StatementFormat.text(key, Math.max(keyLength, 0));
    }
}
