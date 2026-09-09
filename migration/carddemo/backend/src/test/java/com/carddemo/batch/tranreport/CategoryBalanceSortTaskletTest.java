package com.carddemo.batch.tranreport;

import com.carddemo.common.domain.TransactionCategoryBalanceRecord;
import com.carddemo.common.domain.TransactionCategoryBalanceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** STEP10R of PRTCATBL: the DFSORT SORT/OUTREC statements (app/jcl/PRTCATBL.jcl:43-63). */
class CategoryBalanceSortTaskletTest {

    @TempDir
    Path dir;

    /** The 50-byte unload image the REPRO step writes (app/cpy/CVTRA01Y.cpy). */
    @Test
    void writesTheFiftyByteUnloadImage() {
        String image = CategoryBalanceRecordImage.of(balance(11L, "01", 5, new BigDecimal("1234.56")));

        assertThat(image).hasSize(50);
        assertThat(image.substring(0, 11)).isEqualTo("00000000011");
        assertThat(image.substring(11, 13)).isEqualTo("01");
        assertThat(image.substring(13, 17)).isEqualTo("0005");
        assertThat(image.substring(17, 28)).isEqualTo("0000012345F");
        assertThat(image.substring(28)).isBlank();
    }

    /** A negative balance keeps the zoned overpunch sign in the unload image. */
    @Test
    void writesNegativeBalancesWithAnOverpunchSign() {
        String image = CategoryBalanceRecordImage.of(balance(11L, "01", 5, new BigDecimal("-1234.56")));

        assertThat(image.substring(17, 28)).isEqualTo("0000012345O");
    }

    /** SORT FIELDS=(TRANCAT-ACCT-ID,A,TRANCAT-TYPE-CD,A,TRANCAT-CD,A). */
    @Test
    void sortsByAccountThenTypeThenCategory() throws Exception {
        List<String> report = run(List.of(
                CategoryBalanceRecordImage.of(balance(2L, "01", 1, new BigDecimal("1.00"))),
                CategoryBalanceRecordImage.of(balance(1L, "03", 2, new BigDecimal("2.00"))),
                CategoryBalanceRecordImage.of(balance(1L, "01", 9, new BigDecimal("3.00"))),
                CategoryBalanceRecordImage.of(balance(1L, "01", 2, new BigDecimal("4.00")))));

        assertThat(report).extracting(line -> line.substring(0, 19))
                .containsExactly(
                        "00000000001 01 0002",
                        "00000000001 01 0009",
                        "00000000001 03 0002",
                        "00000000002 01 0001");
    }

    /**
     * OUTREC FIELDS: the three keys separated by single blanks, the balance
     * edited into {@code TTTTTTTTT.TT} - every position a T, so no zero
     * suppression - and 9 trailing blanks. 11+1+2+1+4+1+12+9 = 41 characters,
     * although SORTOUT declares LRECL=40 (PRTCATBL.jcl:61).
     */
    @Test
    void formatsTheOutrecRecordAsFortyOneCharacters() throws Exception {
        List<String> report = run(List.of(
                CategoryBalanceRecordImage.of(balance(50L, "05", 4, new BigDecimal("1234.56")))));

        assertThat(report).containsExactly("00000000050 05 0004 000001234.56         ");
        assertThat(report.get(0)).hasSize(41);
    }

    /**
     * {@code EDIT=(TTTTTTTTT.TT)} has no sign position and DFSORT's default is
     * SIGNS=(,,,), so a negative balance prints exactly like a positive one.
     * That is legacy behaviour, reproduced rather than fixed.
     */
    @Test
    void printsNegativeBalancesWithoutASign() throws Exception {
        List<String> report = run(List.of(
                CategoryBalanceRecordImage.of(balance(50L, "05", 4, new BigDecimal("-1234.56")))));

        assertThat(report).containsExactly("00000000050 05 0004 000001234.56         ");
    }

    private List<String> run(List<String> images) throws Exception {
        Path input = dir.resolve("TCATBALF.BKUP");
        Path output = dir.resolve("TCATBALF.REPT");
        Files.write(input, images, StandardCharsets.UTF_8);

        StepExecution stepExecution = new StepExecution("STEP10R", null);
        new CategoryBalanceSortTasklet(input, output)
                .execute(new StepContribution(stepExecution), null);

        return Files.readAllLines(output, StandardCharsets.UTF_8);
    }

    private static TransactionCategoryBalanceRecord balance(long acctId, String typeCd, int catCd, BigDecimal bal) {
        TransactionCategoryBalanceRecord record = new TransactionCategoryBalanceRecord();
        record.setId(new TransactionCategoryBalanceId(acctId, typeCd, catCd));
        record.setBal(bal);
        return record;
    }
}
