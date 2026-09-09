package com.carddemo.batch.authpurge;

import com.carddemo.common.domain.PendingAuthDetailId;
import com.carddemo.common.domain.PendingAuthDetailRecord;
import com.carddemo.common.domain.PendingAuthSummaryRecord;

import java.math.BigDecimal;

/**
 * The unload/load record layout of the {@code PAUTSUM0} and {@code PAUTDTL1}
 * segments.
 *
 * <p>PAUDBUNL writes the raw segment image ({@code OPFIL1-REC PIC X(100)}, and
 * {@code OPFIL2-REC} = the {@code S9(11) COMP-3} root key followed by
 * {@code PIC X(200)}); PAUDBLOD reads those same images back. Those records are
 * EBCDIC with packed-decimal fields, and the repository ships only an EBCDIC IMS
 * image and no ASCII PAUT fixture, so the migrated pair defines an ASCII
 * rendering instead (boundary decision BD-6): the same fields, in copybook
 * order, at fixed widths, with packed numerics expanded to a sign character plus
 * zero-padded digits and an implied two decimal places. Load and unload are
 * exact inverses of each other, which is the property the legacy pair has and
 * the property the round-trip test asserts.
 */
public final class PendingAuthSegmentFormat {

    /** Length of an unloaded {@code PAUTSUM0} record. */
    public static final int ROOT_LENGTH = 113;

    /** Length of an unloaded {@code PAUTDTL1} record, root key included. */
    public static final int CHILD_LENGTH = 212;

    private PendingAuthSegmentFormat() {
    }

    public static String formatRoot(PendingAuthSummaryRecord s) {
        StringBuilder sb = new StringBuilder(ROOT_LENGTH);
        sb.append(num(s.getPaAcctId(), 11));
        sb.append(num(s.getPaCustId(), 9));
        sb.append(text(s.getPaAuthStatus(), 1));
        sb.append(text(s.getPaAccountStatus1(), 2));
        sb.append(text(s.getPaAccountStatus2(), 2));
        sb.append(text(s.getPaAccountStatus3(), 2));
        sb.append(text(s.getPaAccountStatus4(), 2));
        sb.append(text(s.getPaAccountStatus5(), 2));
        sb.append(amount(s.getPaCreditLimit(), 9));
        sb.append(amount(s.getPaCashLimit(), 9));
        sb.append(amount(s.getPaCreditBalance(), 9));
        sb.append(amount(s.getPaCashBalance(), 9));
        sb.append(signedInt(s.getPaApprovedAuthCnt(), 4));
        sb.append(signedInt(s.getPaDeclinedAuthCnt(), 4));
        sb.append(amount(s.getPaApprovedAuthAmt(), 9));
        sb.append(amount(s.getPaDeclinedAuthAmt(), 9));
        return sb.toString();
    }

    public static PendingAuthSummaryRecord parseRoot(String line) {
        Cursor c = new Cursor(line);
        PendingAuthSummaryRecord s = new PendingAuthSummaryRecord();
        s.setPaAcctId(c.number(11));
        s.setPaCustId(c.number(9));
        s.setPaAuthStatus(c.text(1));
        s.setPaAccountStatus1(c.text(2));
        s.setPaAccountStatus2(c.text(2));
        s.setPaAccountStatus3(c.text(2));
        s.setPaAccountStatus4(c.text(2));
        s.setPaAccountStatus5(c.text(2));
        s.setPaCreditLimit(c.amount(9));
        s.setPaCashLimit(c.amount(9));
        s.setPaCreditBalance(c.amount(9));
        s.setPaCashBalance(c.amount(9));
        s.setPaApprovedAuthCnt(c.signedInt(4));
        s.setPaDeclinedAuthCnt(c.signedInt(4));
        s.setPaApprovedAuthAmt(c.amount(9));
        s.setPaDeclinedAuthAmt(c.amount(9));
        return s;
    }

    public static String formatChild(PendingAuthDetailRecord d) {
        PendingAuthDetailId id = d.getId();
        StringBuilder sb = new StringBuilder(CHILD_LENGTH);
        sb.append(num(id.getPaAcctId(), 11));
        sb.append(num(Long.valueOf(id.getPaAuthDate9c()), 5));
        sb.append(num(id.getPaAuthTime9c(), 9));
        sb.append(text(d.getPaAuthOrigDate(), 6));
        sb.append(text(d.getPaAuthOrigTime(), 6));
        sb.append(text(d.getPaCardNum(), 16));
        sb.append(text(d.getPaAuthType(), 4));
        sb.append(text(d.getPaCardExpiryDate(), 4));
        sb.append(text(d.getPaMessageType(), 6));
        sb.append(text(d.getPaMessageSource(), 6));
        sb.append(text(d.getPaAuthIdCode(), 6));
        sb.append(text(d.getPaAuthRespCode(), 2));
        sb.append(text(d.getPaAuthRespReason(), 4));
        sb.append(num(d.getPaProcessingCode(), 6));
        sb.append(amount(d.getPaTransactionAmt(), 10));
        sb.append(amount(d.getPaApprovedAmt(), 10));
        sb.append(text(d.getPaMerchantCatagoryCode(), 4));
        sb.append(text(d.getPaAcqrCountryCode(), 3));
        sb.append(num(d.getPaPosEntryMode() == null ? null : d.getPaPosEntryMode().longValue(), 2));
        sb.append(text(d.getPaMerchantId(), 15));
        sb.append(text(d.getPaMerchantName(), 22));
        sb.append(text(d.getPaMerchantCity(), 13));
        sb.append(text(d.getPaMerchantState(), 2));
        sb.append(text(d.getPaMerchantZip(), 9));
        sb.append(text(d.getPaTransactionId(), 15));
        sb.append(text(d.getPaMatchStatus(), 1));
        sb.append(text(d.getPaAuthFraud(), 1));
        sb.append(text(d.getPaFraudRptDate(), 8));
        return sb.toString();
    }

    public static PendingAuthDetailRecord parseChild(String line) {
        Cursor c = new Cursor(line);
        PendingAuthDetailRecord d = new PendingAuthDetailRecord();
        Long acctId = c.number(11);
        Long date9c = c.number(5);
        Long time9c = c.number(9);
        d.setId(new PendingAuthDetailId(acctId,
                date9c == null ? null : date9c.intValue(), time9c));
        d.setPaAuthOrigDate(c.text(6));
        d.setPaAuthOrigTime(c.text(6));
        d.setPaCardNum(c.text(16));
        d.setPaAuthType(c.text(4));
        d.setPaCardExpiryDate(c.text(4));
        d.setPaMessageType(c.text(6));
        d.setPaMessageSource(c.text(6));
        d.setPaAuthIdCode(c.text(6));
        d.setPaAuthRespCode(c.text(2));
        d.setPaAuthRespReason(c.text(4));
        d.setPaProcessingCode(c.number(6));
        d.setPaTransactionAmt(c.amount(10));
        d.setPaApprovedAmt(c.amount(10));
        d.setPaMerchantCatagoryCode(c.text(4));
        d.setPaAcqrCountryCode(c.text(3));
        Long posEntryMode = c.number(2);
        d.setPaPosEntryMode(posEntryMode == null ? null : posEntryMode.intValue());
        d.setPaMerchantId(c.text(15));
        d.setPaMerchantName(c.text(22));
        d.setPaMerchantCity(c.text(13));
        d.setPaMerchantState(c.text(2));
        d.setPaMerchantZip(c.text(9));
        d.setPaTransactionId(c.text(15));
        d.setPaMatchStatus(c.text(1));
        d.setPaAuthFraud(c.text(1));
        d.setPaFraudRptDate(c.text(8));
        return d;
    }

    /**
     * The root key PAUDBLOD reads from {@code INFILE2} to position the parent,
     * or null when it is not numeric — {@code IF ROOT-SEG-KEY IS NUMERIC}
     * (PAUDBLOD.CBL:283) skips such a record without an insert.
     */
    public static Long childRootKey(String line) {
        String raw = line == null || line.length() < 11 ? "" : line.substring(0, 11).trim();
        if (raw.isEmpty() || !raw.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return Long.valueOf(raw);
    }

    private static String text(String value, int width) {
        String v = value == null ? "" : value;
        return v.length() >= width ? v.substring(0, width) : v + " ".repeat(width - v.length());
    }

    private static String num(Long value, int width) {
        return String.format("%0" + width + "d", value == null ? 0L : value);
    }

    private static String signedInt(Integer value, int width) {
        int v = value == null ? 0 : value;
        return (v < 0 ? "-" : "+") + String.format("%0" + width + "d", Math.abs(v));
    }

    /** Sign plus {@code digits + 2} implied-decimal positions. */
    private static String amount(BigDecimal value, int digits) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        BigDecimal scaled = v.setScale(2, java.math.RoundingMode.HALF_UP);
        String unscaled = scaled.abs().unscaledValue().toString();
        int width = digits + 2;
        if (unscaled.length() > width) {
            unscaled = unscaled.substring(unscaled.length() - width);
        }
        return (scaled.signum() < 0 ? "-" : "+") + "0".repeat(width - unscaled.length()) + unscaled;
    }

    /** Sequential reader over a fixed-width record. */
    private static final class Cursor {
        private final String line;
        private int pos;

        Cursor(String line) {
            this.line = line;
        }

        String text(int width) {
            return take(width).trim();
        }

        Long number(int width) {
            String raw = take(width).trim();
            return raw.isEmpty() ? null : Long.valueOf(raw);
        }

        Integer signedInt(int width) {
            String raw = take(width + 1).trim();
            return raw.isEmpty() ? null : Integer.valueOf(raw.replace("+", ""));
        }

        BigDecimal amount(int digits) {
            String raw = take(digits + 3).trim();
            if (raw.isEmpty()) {
                return null;
            }
            boolean negative = raw.startsWith("-");
            BigDecimal value = new BigDecimal(raw.substring(1)).movePointLeft(2);
            return negative ? value.negate() : value;
        }

        private String take(int width) {
            int end = Math.min(pos + width, line.length());
            String slice = pos >= line.length() ? "" : line.substring(pos, end);
            pos += width;
            return slice;
        }
    }
}
