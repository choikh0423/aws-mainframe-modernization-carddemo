package com.carddemo.batch.intcalc;

import com.carddemo.common.batch.FixedWidthRecord;
import com.carddemo.common.domain.TransactionRecord;

import java.math.BigDecimal;

/**
 * The 350-byte sequential image of a {@code CVTRA05Y TRAN-RECORD}, as written to
 * the SYSTRAN and TRANSACT.COMBINED generations
 * ({@code INTCALC.jcl TRANSACT DD ... RECFM=F,LRECL=350}).
 *
 * <p>Signed {@code PIC S9(09)V99} DISPLAY amounts carry their sign as an
 * overpunch on the last digit, the encoding used throughout {@code app/data/ASCII}
 * ({@code 0000005047G} is {@code +50.47}, {@code 0000009190}} is {@code -91.90});
 * {@link FixedWidthRecord} decodes it on the way back in.
 */
final class TransactionRecordLine {

    static final int LENGTH = 350;

    private static final String POSITIVE_OVERPUNCH = "{ABCDEFGHI";
    private static final String NEGATIVE_OVERPUNCH = "}JKLMNOPQR";

    private TransactionRecordLine() {
    }

    static String format(TransactionRecord tran) {
        StringBuilder line = new StringBuilder(LENGTH);
        line.append(text(tran.getId(), 16));
        line.append(text(tran.getTypeCd(), 2));
        line.append(digits(tran.getCatCd(), 4));
        line.append(text(tran.getSource(), 10));
        line.append(text(tran.getDescription(), 100));
        line.append(signed(tran.getAmount(), 11, 2));
        line.append(digits(tran.getMerchantId(), 9));
        line.append(text(tran.getMerchantName(), 50));
        line.append(text(tran.getMerchantCity(), 50));
        line.append(text(tran.getMerchantZip(), 10));
        line.append(text(tran.getCardNum(), 16));
        line.append(text(tran.getOrigTs(), 26));
        line.append(text(tran.getProcTs(), 26));
        line.append(" ".repeat(20));
        return line.toString();
    }

    static TransactionRecord parse(String line) {
        FixedWidthRecord r = new FixedWidthRecord(line, LENGTH);
        TransactionRecord tran = new TransactionRecord();
        tran.setId(r.text(0, 16));
        tran.setTypeCd(r.text(16, 2));
        tran.setCatCd((int) r.number(18, 4));
        tran.setSource(r.text(22, 10));
        tran.setDescription(r.text(32, 100));
        tran.setAmount(r.signed(132, 11, 2));
        tran.setMerchantId(r.number(143, 9));
        tran.setMerchantName(r.text(152, 50));
        tran.setMerchantCity(r.text(202, 50));
        tran.setMerchantZip(r.text(252, 10));
        tran.setCardNum(r.text(262, 16));
        tran.setOrigTs(r.text(278, 26));
        tran.setProcTs(r.text(304, 26));
        return tran;
    }

    /** The 16-byte TRAN-ID a SORT on {@code TRAN-ID,1,16,CH} compares. */
    static String tranIdOf(String line) {
        return line.length() >= 16 ? line.substring(0, 16) : line;
    }

    /** PIC X(n): left aligned, space padded, truncated at n. */
    private static String text(String value, int length) {
        String raw = value == null ? "" : value;
        return raw.length() >= length ? raw.substring(0, length) : raw + " ".repeat(length - raw.length());
    }

    /** PIC 9(n): unsigned, zero padded. */
    private static String digits(Number value, int length) {
        long raw = value == null ? 0L : value.longValue();
        String unpadded = Long.toString(Math.abs(raw));
        return "0".repeat(Math.max(0, length - unpadded.length())) + unpadded;
    }

    /** PIC S9(a)V9(b): zoned decimal, sign as an overpunch on the last digit. */
    private static String signed(BigDecimal value, int length, int scale) {
        BigDecimal raw = value == null ? BigDecimal.ZERO : value;
        String unscaled = raw.setScale(scale, java.math.RoundingMode.UNNECESSARY)
                .abs().movePointRight(scale).toBigIntegerExact().toString();
        String padded = "0".repeat(Math.max(0, length - unscaled.length())) + unscaled;
        int lastDigit = padded.charAt(padded.length() - 1) - '0';
        char overpunch = raw.signum() < 0
                ? NEGATIVE_OVERPUNCH.charAt(lastDigit)
                : POSITIVE_OVERPUNCH.charAt(lastDigit);
        return padded.substring(0, padded.length() - 1) + overpunch;
    }
}
