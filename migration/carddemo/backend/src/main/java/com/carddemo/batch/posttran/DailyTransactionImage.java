package com.carddemo.batch.posttran;

import com.carddemo.common.domain.DailyTransactionRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Renders a DALYTRAN row back into the 350-byte record image of CVTRA06Y.
 *
 * <p>{@code 2500-WRITE-REJECT-REC} (CBTRN02C.cbl:447) copies the record exactly as
 * it was read into the reject file, so the migrated job has to reproduce the
 * input bytes from the row DATALOAD parsed: {@code PIC X} fields padded right
 * with spaces, {@code PIC 9} fields zero padded, and {@code PIC S9(09)V99}
 * written as a zoned decimal whose sign is an overpunch on the last digit.
 */
public final class DailyTransactionImage {

    /** CVTRA06Y RECLN. */
    public static final int RECORD_LENGTH = 350;

    private static final String POSITIVE_OVERPUNCH = "{ABCDEFGHI";
    private static final String NEGATIVE_OVERPUNCH = "}JKLMNOPQR";

    private DailyTransactionImage() {
    }

    public static String render(DailyTransactionRecord daily) {
        StringBuilder image = new StringBuilder(RECORD_LENGTH);
        image.append(text(daily.getId(), 16));
        image.append(text(daily.getTypeCd(), 2));
        image.append(digits(daily.getCatCd(), 4));
        image.append(text(daily.getSource(), 10));
        image.append(text(daily.getDescription(), 100));
        image.append(zoned(daily.getAmount(), 11, 2));
        image.append(digits(daily.getMerchantId(), 9));
        image.append(text(daily.getMerchantName(), 50));
        image.append(text(daily.getMerchantCity(), 50));
        image.append(text(daily.getMerchantZip(), 10));
        image.append(text(daily.getCardNum(), 16));
        image.append(text(daily.getOrigTs(), 26));
        image.append(text(daily.getProcTs(), 26));
        image.append(" ".repeat(20));
        return image.toString();
    }

    /** PIC X(n). */
    static String text(String value, int length) {
        String raw = value == null ? "" : value;
        if (raw.length() >= length) {
            return raw.substring(0, length);
        }
        return raw + " ".repeat(length - raw.length());
    }

    /** PIC 9(n). */
    static String digits(Number value, int length) {
        long raw = value == null ? 0L : value.longValue();
        String rendered = Long.toString(Math.abs(raw));
        if (rendered.length() >= length) {
            return rendered.substring(rendered.length() - length);
        }
        return "0".repeat(length - rendered.length()) + rendered;
    }

    /** PIC S9(a)V9(b) DISPLAY: zoned decimal with the sign overpunched on the last digit. */
    static String zoned(BigDecimal value, int length, int scale) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        BigDecimal unscaled = amount.setScale(scale, RoundingMode.HALF_UP).movePointRight(scale);
        String rendered = digits(unscaled.abs().longValueExact(), length);
        int lastDigit = rendered.charAt(length - 1) - '0';
        String overpunch = amount.signum() < 0 ? NEGATIVE_OVERPUNCH : POSITIVE_OVERPUNCH;
        return rendered.substring(0, length - 1) + overpunch.charAt(lastDigit);
    }
}
