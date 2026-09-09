package com.carddemo.batch.tranreport;

import java.math.BigDecimal;

/**
 * The fields of a TRANSACT record (CVTRA05Y TRAN-RECORD) that CBTRN03C reads
 * out of its TRANFILE input: the report columns, the card number it breaks
 * accounts on, and the processing timestamp it filters on.
 *
 * @param id       TRAN-ID, PIC X(16)
 * @param typeCd   TRAN-TYPE-CD, PIC X(02)
 * @param catCd    TRAN-CAT-CD, PIC 9(04)
 * @param source   TRAN-SOURCE, PIC X(10)
 * @param amount   TRAN-AMT, PIC S9(09)V99
 * @param cardNum  TRAN-CARD-NUM, PIC X(16), space padded to its full width
 *                 because CBTRN03C compares it to WS-CURR-CARD-NUM PIC X(16)
 * @param procDate TRAN-PROC-TS (1:10), the date CBTRN03C range-checks
 */
record PostedTransaction(String id,
                         String typeCd,
                         int catCd,
                         String source,
                         BigDecimal amount,
                         String cardNum,
                         String procDate) {
}
