package com.carddemo.batch.statement;

import com.carddemo.common.batch.FixedWidthRecord;

import java.math.BigDecimal;

/**
 * TRNX-RECORD, app/cpy/COSTM01.CPY (RECLN 350) - the "transaction altered layout"
 * with the card number moved to the front, produced by CREASTMT.JCL STEP010 and
 * read by CBSTM03A through the TRNXFILE DD.
 */
final class TrnxLayout {

    static final int LENGTH = 350;

    /** TRNX-KEY: TRNX-CARD-NUM + TRNX-ID, the KEYS(32 0) of the work KSDS. */
    static final int KEY_LENGTH = 32;

    /** TRNX-REST, the 318 bytes CBSTM03A keeps per table slot (CBSTM03A.CBL:233). */
    static final int REST_LENGTH = 318;

    private TrnxLayout() {
    }

    /**
     * OUTREC FIELDS=(1:263,16,17:1,262,279:279,50) (CREASTMT.JCL:54): card number,
     * then TRAN-ID through TRAN-MERCHANT-ZIP, then 50 bytes from TRAN-ORIG-TS. The
     * third field is two bytes short of ORIG-TS + PROC-TS, so TRNX-PROC-TS keeps
     * only the first 24 characters of the processing timestamp and bytes 329-350
     * stay blank.
     */
    static String fromTransact(String transactRecord) {
        String record = StatementFormat.text(transactRecord, TransactLayout.LENGTH);
        return StatementFormat.text(
                record.substring(262, 278) + record.substring(0, 262) + record.substring(278, 328),
                LENGTH);
    }

    static String cardNumber(String record) {
        return record.substring(0, 16);
    }

    static String transactionId(String record) {
        return record.substring(16, 32);
    }

    static String rest(String record) {
        return record.substring(32, 32 + REST_LENGTH);
    }

    static String typeCode(String record) {
        return record.substring(32, 34);
    }

    static int categoryCode(String record) {
        return Integer.parseInt(record.substring(34, 38).trim());
    }

    static String source(String record) {
        return record.substring(38, 48);
    }

    /** TRNX-DESC PIC X(100); CBSTM03A prints only its first 49 columns. */
    static String description(String record) {
        return record.substring(48, 148);
    }

    /** TRNX-AMT PIC S9(09)V99. */
    static BigDecimal amount(String record) {
        return new FixedWidthRecord(record, LENGTH).signed(148, 11, 2);
    }

    static long merchantId(String record) {
        return Long.parseLong(record.substring(159, 168).trim());
    }

    static String merchantName(String record) {
        return record.substring(168, 218);
    }

    static String merchantCity(String record) {
        return record.substring(218, 268);
    }

    static String merchantZip(String record) {
        return record.substring(268, 278);
    }

    static String originalTimestamp(String record) {
        return record.substring(278, 304);
    }

    static String processingTimestamp(String record) {
        return record.substring(304, 330);
    }

    /** Renders a work row back into the 350-byte record CBSTM03B hands over. */
    static String render(StatementWorkTransaction work) {
        StringBuilder record = new StringBuilder(LENGTH);
        record.append(StatementFormat.text(work.getCardNum(), 16));
        record.append(StatementFormat.text(work.getTranId(), 16));
        record.append(StatementFormat.text(work.getTypeCd(), 2));
        record.append(StatementFormat.digits(work.getCatCd(), 4));
        record.append(StatementFormat.text(work.getSource(), 10));
        record.append(StatementFormat.text(work.getDescription(), 100));
        record.append(StatementFormat.zoned(work.getAmount(), 11, 2));
        record.append(StatementFormat.digits(work.getMerchantId(), 9));
        record.append(StatementFormat.text(work.getMerchantName(), 50));
        record.append(StatementFormat.text(work.getMerchantCity(), 50));
        record.append(StatementFormat.text(work.getMerchantZip(), 10));
        record.append(StatementFormat.text(work.getOrigTs(), 26));
        record.append(StatementFormat.text(work.getProcTs(), 26));
        return StatementFormat.text(record.toString(), LENGTH);
    }

    /** REPRO of one sequential record into the keyed work store (STEP020). */
    static StatementWorkTransaction toWorkTransaction(String record) {
        StatementWorkTransaction work = new StatementWorkTransaction();
        work.setCardNum(cardNumber(record).trim());
        work.setTranId(transactionId(record).trim());
        work.setTypeCd(typeCode(record).trim());
        work.setCatCd(categoryCode(record));
        work.setSource(source(record).trim());
        work.setDescription(description(record).trim());
        work.setAmount(amount(record));
        work.setMerchantId(merchantId(record));
        work.setMerchantName(merchantName(record).trim());
        work.setMerchantCity(merchantCity(record).trim());
        work.setMerchantZip(merchantZip(record).trim());
        work.setOrigTs(originalTimestamp(record).trim());
        work.setProcTs(processingTimestamp(record).trim());
        return work;
    }
}
