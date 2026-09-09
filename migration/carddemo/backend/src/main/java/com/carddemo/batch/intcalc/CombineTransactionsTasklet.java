package com.carddemo.batch.intcalc;

import com.carddemo.common.domain.TransactionRecord;
import com.carddemo.common.repository.TransactionRepository;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@code STEP05R EXEC PGM=SORT} of COMBTRAN.
 *
 * <pre>
 * //SORTIN  DD DSN=AWS.M2.CARDDEMO.TRANSACT.BKUP(0)
 * //        DD DSN=AWS.M2.CARDDEMO.SYSTRAN(0)
 * //SYSIN   DD * SORT FIELDS=(TRAN-ID,A)     TRAN-ID,1,16,CH
 * </pre>
 *
 * <p>The first SORTIN dataset is the transaction master's backup generation, taken
 * by the operations chain before the interest run; in the migrated application the
 * master is the {@code transactions} table, so the table itself is that input
 * (boundary B-12.3). The second is the SYSTRAN generation INTCALC has just written,
 * which is still a file.
 *
 * <p>{@code CH} is a character collation on bytes 1-16 and there is no
 * {@code SUM FIELDS}, so records are ordered by the ASCII/EBCDIC ordering of the
 * transaction id and duplicates are kept, not merged.
 */
class CombineTransactionsTasklet implements Tasklet {

    private final TransactionRepository transactions;
    private final String systranFile;
    private final String combinedFile;

    CombineTransactionsTasklet(TransactionRepository transactions,
                               String systranFile,
                               String combinedFile) {
        this.transactions = transactions;
        this.systranFile = systranFile;
        this.combinedFile = combinedFile;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws IOException {
        Path systran = Path.of(systranFile);
        if (!Files.isRegularFile(systran)) {
            throw new IllegalStateException(
                    "SORTIN allocation failed: no SYSTRAN generation at " + systranFile);
        }

        List<String> sortin = new ArrayList<>();
        for (TransactionRecord backup : transactions.findAll(Sort.by("id"))) {
            sortin.add(TransactionRecordLine.format(backup));
        }
        sortin.addAll(Files.readAllLines(systran, StandardCharsets.UTF_8));
        sortin.sort(Comparator.comparing(TransactionRecordLine::tranIdOf));

        SystranFiles.createParentDirectory(combinedFile);
        Files.write(Path.of(combinedFile), sortin, StandardCharsets.UTF_8);

        contribution.incrementWriteCount(sortin.size());
        return RepeatStatus.FINISHED;
    }
}
