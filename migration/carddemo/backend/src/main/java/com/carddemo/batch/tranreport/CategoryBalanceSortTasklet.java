package com.carddemo.batch.tranreport;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * STEP10R of PRTCATBL (app/jcl/PRTCATBL.jcl:43-63): sort the unloaded TCATBALF
 * records by account, transaction type and category, and reformat them into the
 * printable record.
 *
 * <pre>
 *  SORT FIELDS=(TRANCAT-ACCT-ID,A,TRANCAT-TYPE-CD,A,TRANCAT-CD,A)
 *  OUTREC FIELDS=(TRANCAT-ACCT-ID,X,
 *      TRANCAT-TYPE-CD,X,
 *      TRANCAT-CD,X,
 *      TRAN-CAT-BAL,EDIT=(TTTTTTTTT.TT),9X)
 * </pre>
 *
 * <p>The three key fields are copied as they stand (zero-padded digits, so a
 * character sort orders them the same way DFSORT's ZD/CH keys do) separated by
 * single blanks, and the balance is edited into {@code nnnnnnnnn.nn}. Every
 * digit position of {@code EDIT=(TTTTTTTTT.TT)} is a {@code T}, so digits are
 * printed as they are with no zero suppression, and the pattern carries no sign
 * position: with DFSORT's default {@code SIGNS}, a negative balance prints
 * exactly like a positive one. That is legacy behaviour and is reproduced.
 *
 * <p>The reformatted record is 41 bytes long (11+1+2+1+4+1+12+9) while the
 * SORTOUT DD declares {@code LRECL=40} (PRTCATBL.jcl:61) - a contradiction in
 * the shipped JCL. The OUTREC statement is the specification of what the report
 * contains, so the migrated step writes all 41 characters.
 */
class CategoryBalanceSortTasklet implements Tasklet {

    /** The trailing {@code 9X} of the OUTREC statement. */
    private static final int TRAILING_BLANKS = 9;

    private final Path input;
    private final Path output;

    CategoryBalanceSortTasklet(Path input, Path output) {
        this.input = input;
        this.output = output;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws IOException {
        List<String> report;
        try (var lines = Files.lines(input, StandardCharsets.UTF_8)) {
            report = lines
                    .sorted(Comparator.comparing(CategoryBalanceSortTasklet::sortKey))
                    .map(CategoryBalanceSortTasklet::outrec)
                    .toList();
        }
        Files.write(output, report, StandardCharsets.UTF_8);
        contribution.incrementWriteCount(report.size());
        return RepeatStatus.FINISHED;
    }

    private static String sortKey(String record) {
        return field(record, CategoryBalanceRecordImage.ACCT_ID, CategoryBalanceRecordImage.ACCT_ID_LENGTH)
                + field(record, CategoryBalanceRecordImage.TYPE_CD, CategoryBalanceRecordImage.TYPE_CD_LENGTH)
                + field(record, CategoryBalanceRecordImage.CAT_CD, CategoryBalanceRecordImage.CAT_CD_LENGTH);
    }

    private static String outrec(String record) {
        return field(record, CategoryBalanceRecordImage.ACCT_ID, CategoryBalanceRecordImage.ACCT_ID_LENGTH) + " "
                + field(record, CategoryBalanceRecordImage.TYPE_CD, CategoryBalanceRecordImage.TYPE_CD_LENGTH) + " "
                + field(record, CategoryBalanceRecordImage.CAT_CD, CategoryBalanceRecordImage.CAT_CD_LENGTH) + " "
                + edited(field(record, CategoryBalanceRecordImage.BAL, CategoryBalanceRecordImage.BAL_LENGTH))
                + " ".repeat(TRAILING_BLANKS);
    }

    /** {@code EDIT=(TTTTTTTTT.TT)} applied to an 11-digit zoned decimal field. */
    private static String edited(String zoned) {
        StringBuilder digits = new StringBuilder(zoned);
        char last = digits.charAt(digits.length() - 1);
        int index = "{ABCDEFGHI".indexOf(last);
        if (index < 0) {
            index = "}JKLMNOPQR".indexOf(last);
        }
        if (index >= 0) {
            digits.setCharAt(digits.length() - 1, (char) ('0' + index));
        }
        return digits.substring(0, 9) + "." + digits.substring(9);
    }

    private static String field(String record, int offset, int length) {
        if (record.length() <= offset) {
            return " ".repeat(length);
        }
        String value = record.substring(offset, Math.min(record.length(), offset + length));
        return value.length() == length ? value : value + " ".repeat(length - value.length());
    }
}
