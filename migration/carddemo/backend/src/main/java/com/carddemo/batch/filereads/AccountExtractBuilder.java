package com.carddemo.batch.filereads;

import com.carddemo.common.domain.AccountRecord;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * Builds the derived records of CBACT01C — paragraphs {@code 1300-POPUL-ACCT-RECORD},
 * {@code 1400-POPUL-ARRAY-RECORD} and {@code 1500-POPUL-VBRC-RECORD}
 * (`CBACT01C.cbl:215-285`).
 *
 * <p>The builder is stateful for one run, because the program is: {@code OUT-ACCT-REC}
 * lives in the FD record area and is never re-initialised, so the cycle-debit field
 * carries over from the previous account whenever the current one is non-zero
 * (FR-A9).
 */
public class AccountExtractBuilder {

    private static final BigDecimal ZERO_DEBIT_SUBSTITUTE = new BigDecimal("2525.00");
    private static final BigDecimal ARRAY_DEBIT_1 = new BigDecimal("1005.00");
    private static final BigDecimal ARRAY_DEBIT_2 = new BigDecimal("1525.00");
    private static final BigDecimal ARRAY_BALANCE_3 = new BigDecimal("-1025.00");
    private static final BigDecimal ARRAY_DEBIT_3 = new BigDecimal("-2500.00");

    /** OUT-ACCT-CURR-CYC-DEBIT as the record area holds it; zero before the first write. */
    private BigDecimal outCycDebit = BigDecimal.ZERO;

    public AccountExtract build(AccountRecord account) {
        return new AccountExtract(outRecord(account), arrayRecord(account),
                vbrcRec1(account), vbrcRec2(account));
    }

    /** 1300-POPUL-ACCT-RECORD — the 107-byte OUT-ACCT-REC. */
    private byte[] outRecord(AccountRecord account) {
        LegacyDateFormatter.Result reissue = LegacyDateFormatter.convert(
                LegacyDateFormatter.TYPE_YYYY_MM_DD, LegacyDateFormatter.TYPE_YYYY_MM_DD,
                account.getReissueDate());
        if (account.getCurrCycDebit().signum() == 0) {
            outCycDebit = ZERO_DEBIT_SUBSTITUTE;
        }

        String display = CobolPicture.unsigned(account.getAcctId(), 11)
                + CobolPicture.text(account.getActiveStatus(), 1)
                + CobolPicture.signed(account.getCurrBal(), 10, 2)
                + CobolPicture.signed(account.getCreditLimit(), 10, 2)
                + CobolPicture.signed(account.getCashCreditLimit(), 10, 2)
                + CobolPicture.text(account.getOpenDate(), 10)
                + CobolPicture.text(account.getExpiraionDate(), 10)
                + CobolPicture.text(reissue.outDate(), 10)
                + CobolPicture.signed(account.getCurrCycCredit(), 10, 2);
        byte[] record = new byte[107];
        byte[] head = display.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(head, 0, record, 0, head.length);
        byte[] packedDebit = CobolPicture.packed(outCycDebit, 10, 2);
        System.arraycopy(packedDebit, 0, record, head.length, packedDebit.length);
        byte[] groupId = CobolPicture.text(account.getGroupId(), 10).getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(groupId, 0, record, head.length + packedDebit.length, groupId.length);
        return record;
    }

    /**
     * 1400-POPUL-ARRAY-RECORD — the 110-byte ARR-ARRAY-REC. The record is
     * {@code INITIALIZE}d per account (`CBACT01C.cbl:169`), so occurrences 4 and 5 stay
     * zero and the trailing filler stays blank; occurrences 1 to 3 mix the account
     * balance with the literals of the paragraph.
     */
    private byte[] arrayRecord(AccountRecord account) {
        BigDecimal balance = account.getCurrBal();
        BigDecimal[][] occurrences = {
                {balance, ARRAY_DEBIT_1},
                {balance, ARRAY_DEBIT_2},
                {ARRAY_BALANCE_3, ARRAY_DEBIT_3},
                {BigDecimal.ZERO, BigDecimal.ZERO},
                {BigDecimal.ZERO, BigDecimal.ZERO},
        };
        byte[] record = new byte[110];
        int offset = 0;
        byte[] id = CobolPicture.unsigned(account.getAcctId(), 11).getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(id, 0, record, offset, id.length);
        offset += id.length;
        for (BigDecimal[] occurrence : occurrences) {
            byte[] bal = CobolPicture.signed(occurrence[0], 10, 2).getBytes(StandardCharsets.ISO_8859_1);
            System.arraycopy(bal, 0, record, offset, bal.length);
            offset += bal.length;
            byte[] debit = CobolPicture.packed(occurrence[1], 10, 2);
            System.arraycopy(debit, 0, record, offset, debit.length);
            offset += debit.length;
        }
        byte[] filler = "    ".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(filler, 0, record, offset, filler.length);
        return record;
    }

    /** VBRC-REC1 — 12 bytes: account id and active status. */
    private String vbrcRec1(AccountRecord account) {
        return CobolPicture.unsigned(account.getAcctId(), 11)
                + CobolPicture.text(account.getActiveStatus(), 1);
    }

    /**
     * VBRC-REC2 — 39 bytes: account id, balance, credit limit and the year of the raw
     * reissue date, taken through the {@code WS-ACCT-REISSUE-DATE} redefinition
     * (`CBACT01C.cbl:131-137,282`).
     */
    private String vbrcRec2(AccountRecord account) {
        String reissueYear = CobolPicture.text(account.getReissueDate(), 10).substring(0, 4);
        return CobolPicture.unsigned(account.getAcctId(), 11)
                + CobolPicture.signed(account.getCurrBal(), 10, 2)
                + CobolPicture.signed(account.getCreditLimit(), 10, 2)
                + reissueYear;
    }
}
