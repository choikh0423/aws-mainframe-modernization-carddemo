package com.carddemo.billpayment.util;

import com.carddemo.billpayment.entity.Account;
import com.carddemo.billpayment.entity.CardXref;
import com.carddemo.billpayment.entity.Transaction;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fixed-width CardDemo record layouts (CVACT01Y, CVACT03Y, CVTRA05Y) as they
 * appear in app/data/ASCII: signed zoned decimals use the EBCDIC overpunch
 * ({ABCDEFGHI for +0..+9, }JKLMNOPQR for -0..-9) on the last digit.
 */
public final class CobolRecords {

    private static final String POSITIVE = "{ABCDEFGHI";
    private static final String NEGATIVE = "}JKLMNOPQR";

    private CobolRecords() {
    }

    public static BigDecimal zoned(String field, int scale) {
        char last = field.charAt(field.length() - 1);
        int digit;
        boolean negative = false;
        if (POSITIVE.indexOf(last) >= 0) {
            digit = POSITIVE.indexOf(last);
        } else if (NEGATIVE.indexOf(last) >= 0) {
            digit = NEGATIVE.indexOf(last);
            negative = true;
        } else {
            digit = last - '0';
        }
        String digits = field.substring(0, field.length() - 1).replace(' ', '0') + digit;
        BigDecimal v = new BigDecimal(digits).movePointLeft(scale);
        return negative ? v.negate() : v;
    }

    public static String zoned(BigDecimal value, int length, int scale) {
        BigDecimal scaled = value.setScale(scale, RoundingMode.DOWN);
        String digits = scaled.abs().movePointRight(scale).toBigInteger().toString();
        digits = pad0(digits, length);
        int lastDigit = digits.charAt(length - 1) - '0';
        char sign = scaled.signum() < 0 ? NEGATIVE.charAt(lastDigit) : POSITIVE.charAt(lastDigit);
        return digits.substring(0, length - 1) + sign;
    }

    public static Account parseAccount(String rec) {
        Account a = new Account();
        a.setAcctId(rec.substring(0, 11));
        a.setActiveStatus(rec.substring(11, 12));
        a.setCurrBal(zoned(rec.substring(12, 24), 2));
        a.setCreditLimit(zoned(rec.substring(24, 36), 2));
        a.setCashCreditLimit(zoned(rec.substring(36, 48), 2));
        a.setOpenDate(rec.substring(48, 58));
        a.setExpirationDate(rec.substring(58, 68));
        a.setReissueDate(rec.substring(68, 78));
        a.setCurrCycCredit(zoned(rec.substring(78, 90), 2));
        a.setCurrCycDebit(zoned(rec.substring(90, 102), 2));
        a.setAddrZip(rec.substring(102, 112));
        a.setGroupId(rec.substring(112, 122));
        return a;
    }

    public static String formatAccount(Account a) {
        return pad(a.getAcctId(), 11) + pad(a.getActiveStatus(), 1)
                + zoned(a.getCurrBal(), 12, 2) + zoned(a.getCreditLimit(), 12, 2)
                + zoned(a.getCashCreditLimit(), 12, 2) + pad(a.getOpenDate(), 10)
                + pad(a.getExpirationDate(), 10) + pad(a.getReissueDate(), 10)
                + zoned(a.getCurrCycCredit(), 12, 2) + zoned(a.getCurrCycDebit(), 12, 2)
                + pad(a.getAddrZip(), 10) + pad(a.getGroupId(), 10) + pad("", 178);
    }

    public static CardXref parseXref(String rec) {
        CardXref x = new CardXref();
        x.setCardNum(rec.substring(0, 16));
        x.setCustId(Long.parseLong(rec.substring(16, 25)));
        x.setAcctId(rec.substring(25, 36));
        return x;
    }

    public static Transaction parseTransaction(String rec) {
        Transaction t = new Transaction();
        t.setTranId(rec.substring(0, 16));
        t.setTypeCd(rec.substring(16, 18));
        t.setCatCd(Integer.parseInt(rec.substring(18, 22)));
        t.setSource(rec.substring(22, 32).stripTrailing());
        t.setDescription(rec.substring(32, 132).stripTrailing());
        t.setAmount(zoned(rec.substring(132, 143), 2));
        t.setMerchantId(Long.parseLong(rec.substring(143, 152)));
        t.setMerchantName(rec.substring(152, 202).stripTrailing());
        t.setMerchantCity(rec.substring(202, 252).stripTrailing());
        t.setMerchantZip(rec.substring(252, 262).stripTrailing());
        t.setCardNum(rec.substring(262, 278));
        t.setOrigTs(rec.substring(278, 304));
        t.setProcTs(rec.substring(304, 330));
        return t;
    }

    /** The 350-byte TRAN-RECORD the legacy WRITE would put on TRANSACT. */
    public static String formatTransaction(Transaction t) {
        return pad(t.getTranId(), 16) + pad(t.getTypeCd(), 2) + pad0(String.valueOf(t.getCatCd()), 4)
                + pad(t.getSource(), 10) + pad(t.getDescription(), 100)
                + zoned(t.getAmount(), 11, 2) + pad0(String.valueOf(t.getMerchantId()), 9)
                + pad(t.getMerchantName(), 50) + pad(t.getMerchantCity(), 50)
                + pad(t.getMerchantZip(), 10) + pad(t.getCardNum(), 16)
                + pad(t.getOrigTs(), 26) + pad(t.getProcTs(), 26) + pad("", 20);
    }

    public static String pad(String s, int len) {
        String v = s == null ? "" : s;
        return v.length() >= len ? v.substring(0, len) : v + " ".repeat(len - v.length());
    }

    private static String pad0(String s, int len) {
        return s.length() >= len ? s.substring(s.length() - len) : "0".repeat(len - s.length()) + s;
    }
}
