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
 * The DFSORT step of TRANREPT (app/jcl/TRANREPT.jcl:36-56): keep the unloaded
 * TRANSACT records whose TRAN-PROC-DT falls inside the reporting range, sorted
 * by TRAN-CARD-NUM ascending.
 *
 * <pre>
 * TRAN-CARD-NUM,263,16,ZD
 * TRAN-PROC-DT,305,10,CH
 *  SORT FIELDS=(TRAN-CARD-NUM,A)
 *  INCLUDE COND=(TRAN-PROC-DT,GE,PARM-START-DATE,AND,
 *          TRAN-PROC-DT,LE,PARM-END-DATE)
 * </pre>
 *
 * <p>The two dates are the SYMNAMES constants the CR00 screen substitutes into
 * the submitted JCL (app/cbl/CORPT00C.cbl:449-470); here they are the job's
 * {@code startDate} and {@code endDate} parameters. Both comparisons are
 * character comparisons on a {@code YYYY-MM-DD} field, so they are inclusive
 * and order calendar dates correctly.
 *
 * <p>Sorting by card number groups the transactions of one card together, which
 * is what makes CBTRN03C's account-break logic produce one Account Total per
 * account. A run is a whole step: the tasklet rewrites its output from scratch,
 * as the JCL's {@code DISP=(NEW,CATLG,DELETE)} allocation did.
 */
class TransactionExtractSortTasklet implements Tasklet {

    private static final int CARD_NUM = 262;
    private static final int CARD_NUM_LENGTH = 16;
    private static final int PROC_DT = 304;
    private static final int PROC_DT_LENGTH = 10;

    private final Path input;
    private final Path output;
    private final String startDate;
    private final String endDate;

    TransactionExtractSortTasklet(Path input, Path output, String startDate, String endDate) {
        this.input = input;
        this.output = output;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws IOException {
        List<String> selected;
        try (var lines = Files.lines(input, StandardCharsets.UTF_8)) {
            selected = lines
                    .filter(this::included)
                    .sorted(Comparator.comparing(line -> field(line, CARD_NUM, CARD_NUM_LENGTH)))
                    .toList();
        }
        Files.write(output, selected, StandardCharsets.UTF_8);
        contribution.incrementWriteCount(selected.size());
        return RepeatStatus.FINISHED;
    }

    private boolean included(String line) {
        String procDate = field(line, PROC_DT, PROC_DT_LENGTH);
        return procDate.compareTo(startDate) >= 0 && procDate.compareTo(endDate) <= 0;
    }

    private static String field(String line, int offset, int length) {
        if (line.length() <= offset) {
            return " ".repeat(length);
        }
        String value = line.substring(offset, Math.min(line.length(), offset + length));
        return value.length() == length ? value : value + " ".repeat(length - value.length());
    }
}
