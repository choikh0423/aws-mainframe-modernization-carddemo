package com.carddemo.batch.filereads;

import com.carddemo.common.domain.AccountRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-A7 to FR-A11: the three derived records CBACT01C writes for every account. */
class AccountExtractBuilderTest {

    private final AccountExtractBuilder builder = new AccountExtractBuilder();

    @Test
    void buildsTheHundredAndSevenByteAccountRecord() {
        AccountExtract extract = builder.build(account(1L, "0.00"));

        assertThat(extract.outRecord()).hasSize(107);
        String head = new String(extract.outRecord(), 0, 90, StandardCharsets.ISO_8859_1);
        assertThat(head).isEqualTo("00000000001Y00000001940{00000020200{00000010200{"
                + "2014-11-20" + "2025-05-20" + "20250520  " + "00000000000{");
    }

    @Test
    void convertsTheReissueDateThroughTheDateUtilityReplacement() {
        AccountExtract extract = builder.build(account(1L, "0.00"));

        // COBDATFT in-type 2 / out-type 2 compresses 2025-05-20 to 20250520, and the
        // 10-byte OUT-ACCT-REISSUE-DATE keeps the two trailing blanks.
        String reissue = new String(extract.outRecord(), 68, 10, StandardCharsets.ISO_8859_1);
        assertThat(reissue).isEqualTo("20250520  ");
    }

    @Test
    void writesTheCycleDebitAsPackedDecimal() {
        AccountExtract extract = builder.build(account(1L, "0.00"));

        // A zero cycle debit is replaced by the literal 2525.00 of 1300-POPUL-ACCT-RECORD.
        byte[] packed = new byte[7];
        System.arraycopy(extract.outRecord(), 90, packed, 0, 7);
        assertThat(packed).isEqualTo(CobolPicture.packed(new BigDecimal("2525.00"), 10, 2));
    }

    @Test
    void keepsThePreviousCycleDebitWhenTheAccountHasANonZeroOne() {
        // The FD record area is never re-initialised, so a non-zero debit leaves the
        // previous account's value in the output record (FR-A9, a legacy quirk).
        builder.build(account(1L, "0.00"));
        AccountExtract second = builder.build(account(2L, "150.00"));

        byte[] packed = new byte[7];
        System.arraycopy(second.outRecord(), 90, packed, 0, 7);
        assertThat(packed).isEqualTo(CobolPicture.packed(new BigDecimal("2525.00"), 10, 2));
    }

    @Test
    void leavesTheCycleDebitZeroWhenTheFirstAccountHasANonZeroOne() {
        AccountExtract first = builder.build(account(1L, "150.00"));

        byte[] packed = new byte[7];
        System.arraycopy(first.outRecord(), 90, packed, 0, 7);
        assertThat(packed).isEqualTo(CobolPicture.packed(BigDecimal.ZERO, 10, 2));
    }

    @Test
    void buildsTheHundredAndTenByteArrayRecord() {
        AccountExtract extract = builder.build(account(1L, "0.00"));
        byte[] record = extract.arrayRecord();

        assertThat(record).hasSize(110);
        assertThat(new String(record, 0, 11, StandardCharsets.ISO_8859_1)).isEqualTo("00000000001");
        assertThat(new String(record, 11, 12, StandardCharsets.ISO_8859_1)).isEqualTo("00000001940{");
        assertThat(new String(record, 49, 12, StandardCharsets.ISO_8859_1)).isEqualTo("00000010250}");
        assertThat(new String(record, 106, 4, StandardCharsets.ISO_8859_1)).isEqualTo("    ");
    }

    @Test
    void buildsTheTwoVariableLengthRecords() {
        AccountExtract extract = builder.build(account(1L, "0.00"));

        assertThat(extract.vbrcRec1()).isEqualTo("00000000001Y");
        assertThat(extract.vbrcRec2()).hasSize(39);
        assertThat(extract.vbrcRec2()).isEqualTo("00000000001" + "00000001940{" + "00000020200{" + "2025");
    }

    private AccountRecord account(long id, String cycleDebit) {
        AccountRecord account = new AccountRecord();
        account.setAcctId(id);
        account.setActiveStatus("Y");
        account.setCurrBal(new BigDecimal("194.00"));
        account.setCreditLimit(new BigDecimal("2020.00"));
        account.setCashCreditLimit(new BigDecimal("1020.00"));
        account.setOpenDate("2014-11-20");
        account.setExpiraionDate("2025-05-20");
        account.setReissueDate("2025-05-20");
        account.setCurrCycCredit(BigDecimal.ZERO);
        account.setCurrCycDebit(new BigDecimal(cycleDebit));
        account.setAddrZip("A000000000");
        account.setGroupId("");
        return account;
    }
}
