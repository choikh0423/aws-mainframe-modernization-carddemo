package com.carddemo.batch.tranreport;

import com.carddemo.common.batch.FixedWidthRecord;
import com.carddemo.common.domain.TransactionRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The 350-byte TRANSACT record image (app/cpy/CVTRA05Y.cpy) that the TRANREPT
 * job's unload and sort steps move around.
 *
 * <p>IDCAMS REPRO unloaded the VSAM KSDS byte for byte and DFSORT addressed the
 * result by column, so the migrated STEP05R has to rebuild exactly the same
 * image from the {@code transactions} table for the sort columns
 * (TRAN-CARD-NUM at 263, TRAN-PROC-DT at 305) to line up.
 */
final class TransactionRecordImage {

    static final int LENGTH = 350;

    private static final String POSITIVE_OVERPUNCH = "{ABCDEFGHI";
    private static final String NEGATIVE_OVERPUNCH = "}JKLMNOPQR";

    /** Zero-based offsets and widths, in copybook order. */
    private static final int ID = 0;
    private static final int TYPE_CD = 16;
    private static final int CAT_CD = 18;
    private static final int SOURCE = 22;
    private static final int DESCRIPTION = 32;
    private static final int AMT = 132;
    private static final int MERCHANT_ID = 143;
    private static final int MERCHANT_NAME = 152;
    private static final int MERCHANT_CITY = 202;
    private static final int MERCHANT_ZIP = 252;
    private static final int CARD_NUM = 262;
    private static final int ORIG_TS = 278;
    private static final int PROC_TS = 304;

    private TransactionRecordImage() {
    }

    /** Renders a {@code transactions} row as the record IDCAMS would unload. */
    static String of(TransactionRecord transaction) {
        StringBuilder image = new StringBuilder(" ".repeat(LENGTH));
        put(image, ID, text(transaction.getId(), 16));
        put(image, TYPE_CD, text(transaction.getTypeCd(), 2));
        put(image, CAT_CD, number(transaction.getCatCd(), 4));
        put(image, SOURCE, text(transaction.getSource(), 10));
        put(image, DESCRIPTION, text(transaction.getDescription(), 100));
        put(image, AMT, signed(transaction.getAmount(), 11));
        put(image, MERCHANT_ID, number(transaction.getMerchantId(), 9));
        put(image, MERCHANT_NAME, text(transaction.getMerchantName(), 50));
        put(image, MERCHANT_CITY, text(transaction.getMerchantCity(), 50));
        put(image, MERCHANT_ZIP, text(transaction.getMerchantZip(), 10));
        put(image, CARD_NUM, text(transaction.getCardNum(), 16));
        put(image, ORIG_TS, text(transaction.getOrigTs(), 26));
        put(image, PROC_TS, text(transaction.getProcTs(), 26));
        return image.toString();
    }

    /** Reads the fields CBTRN03C uses out of a TRANFILE record. */
    static PostedTransaction parse(String line) {
        FixedWidthRecord record = new FixedWidthRecord(line, LENGTH);
        String padded = line.length() >= LENGTH ? line : line + " ".repeat(LENGTH - line.length());
        return new PostedTransaction(
                record.text(ID, 16),
                record.text(TYPE_CD, 2),
                (int) record.number(CAT_CD, 4),
                record.text(SOURCE, 10),
                record.signed(AMT, 11, 2),
                padded.substring(CARD_NUM, CARD_NUM + 16),
                padded.substring(PROC_TS, PROC_TS + 10));
    }

    private static void put(StringBuilder image, int offset, String value) {
        image.replace(offset, offset + value.length(), value);
    }

    private static String text(String value, int width) {
        String raw = value == null ? "" : value;
        return raw.length() >= width ? raw.substring(0, width)
                : raw + " ".repeat(width - raw.length());
    }

    private static String number(Number value, int width) {
        long raw = value == null ? 0L : value.longValue();
        String digits = Long.toString(Math.abs(raw));
        return digits.length() >= width ? digits.substring(digits.length() - width)
                : "0".repeat(width - digits.length()) + digits;
    }

    /** PIC S9(a)V9(b) DISPLAY: zoned decimal with the sign overpunched on the last digit. */
    static String signed(BigDecimal value, int width) {
        BigDecimal scaled = (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.DOWN);
        String digits = number(scaled.abs().unscaledValue(), width);
        int last = digits.charAt(width - 1) - '0';
        char overpunch = scaled.signum() < 0
                ? NEGATIVE_OVERPUNCH.charAt(last)
                : POSITIVE_OVERPUNCH.charAt(last);
        return digits.substring(0, width - 1) + overpunch;
    }
}
