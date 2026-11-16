package com.carddemo.statements;

import com.carddemo.batch.statement.Statement;
import com.carddemo.batch.statement.StatementRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * CREASTMT STEP040's third writer: upserts one {@code statement_archive} row per
 * card for the run's cycle, holding exactly the text and HTML lines
 * {@link StatementRenderer} produced for the report files.
 *
 * <p>Rerunning a cycle replaces it: every row the run writes is stamped with the
 * run's {@code generated_ts}, and once the step completes the cycle's rows from
 * earlier runs that this run did not rewrite are deleted. Other cycles are never
 * touched.
 */
@Component
public class StatementArchiveWriter implements ItemWriter<Statement>, StepExecutionListener {

    private static final Logger LOG = LoggerFactory.getLogger(StatementArchiveWriter.class);

    private final StatementArchiveRepository repository;
    private final StatementRenderer renderer;
    private final StatementCycle statementCycle;

    private String cycle;
    private LocalDateTime generatedTs;

    public StatementArchiveWriter(StatementArchiveRepository repository,
                                  StatementRenderer renderer,
                                  StatementCycle statementCycle) {
        this.repository = repository;
        this.renderer = renderer;
        this.statementCycle = statementCycle;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        ExecutionContext jobContext = stepExecution.getJobExecution().getExecutionContext();
        if (!jobContext.containsKey(StatementCycle.CONTEXT_CYCLE)) {
            statementCycle.beforeJob(stepExecution.getJobExecution());
        }
        cycle = jobContext.getString(StatementCycle.CONTEXT_CYCLE);
        generatedTs = LocalDateTime.parse(jobContext.getString(StatementCycle.CONTEXT_GENERATED_TS));
    }

    @Override
    public void write(Chunk<? extends Statement> chunk) {
        for (Statement statement : chunk) {
            String cardNum = statement.cardNumber().trim();
            StatementArchiveRecord row = repository.findByCardNumAndCycle(cardNum, cycle)
                    .orElseGet(StatementArchiveRecord::new);
            row.setAcctId(statement.accountId());
            row.setCardNum(cardNum);
            row.setCycle(cycle);
            row.setGeneratedTs(generatedTs);
            row.setTxnCount(statement.transactionRecords().size());
            row.setTotalAmount(statement.totalAmount());
            row.setTextBody(String.join("\n", renderer.text(statement)));
            row.setHtmlBody(String.join("\n", renderer.html(statement)));
            repository.save(row);
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (stepExecution.getStatus() == BatchStatus.COMPLETED) {
            int removed = repository.deleteOtherRuns(cycle, generatedTs);
            LOG.info("archived {} statements for cycle {} ({} stale rows removed)",
                    stepExecution.getWriteCount(), cycle, removed);
        }
        return null;
    }
}
