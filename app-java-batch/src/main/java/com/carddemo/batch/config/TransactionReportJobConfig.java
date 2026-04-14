package com.carddemo.batch.config;

import com.carddemo.batch.model.Transaction;
import com.carddemo.batch.processor.TransactionReportProcessor;
import com.carddemo.batch.repository.TransactionRepository;
import com.carddemo.batch.writer.ReportFileWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Spring Batch job configuration for TRANREPT.
 * Migrated from CBTRN03C.cbl and TRANREPT.jcl.
 *
 * The original JCL has 3 steps (REPRO, SORT, REPORT).
 * In Java, we read directly from the Transaction table with sorting/filtering
 * (replacing REPRO + SORT) and produce a formatted text report.
 */
@Configuration
public class TransactionReportJobConfig {

    private static final Logger log = LoggerFactory.getLogger(TransactionReportJobConfig.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final TransactionRepository transactionRepository;
    private final TransactionReportProcessor reportProcessor;
    private final ReportFileWriter reportFileWriter;

    public TransactionReportJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            TransactionRepository transactionRepository,
            TransactionReportProcessor reportProcessor,
            ReportFileWriter reportFileWriter) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.transactionRepository = transactionRepository;
        this.reportProcessor = reportProcessor;
        this.reportFileWriter = reportFileWriter;
    }

    @Bean
    public Job transactionReportJob() {
        return new JobBuilder("transactionReportJob", jobRepository)
                .start(generateReportStep(null, null))
                .build();
    }

    @Bean
    @StepScope
    public Step generateReportStep(
            @Value("#{jobParameters['startDate']}") String startDate,
            @Value("#{jobParameters['endDate']}") String endDate) {
        return new StepBuilder("generateReportStep", jobRepository)
                .tasklet(reportTasklet(startDate, endDate), transactionManager)
                .build();
    }

    /**
     * Tasklet-based approach for reporting since we need control-break logic
     * (account totals, page totals, grand totals) that doesn't fit chunk processing well.
     */
    @Bean
    @StepScope
    public Tasklet reportTasklet(
            @Value("#{jobParameters['startDate']}") String startDate,
            @Value("#{jobParameters['endDate']}") String endDate) {
        return (StepContribution contribution, ChunkContext chunkContext) -> {
            String start = startDate != null ? startDate : "0000-00-00";
            String end = endDate != null ? endDate : "9999-99-99";

            log.info("Generating transaction report for date range: {} to {}", start, end);

            List<Transaction> transactions =
                    transactionRepository.findByProcTimestampDateRange(start, end);

            reportFileWriter.open();
            try {
                reportFileWriter.writePageHeader(start, end);

                String currentCard = null;
                BigDecimal accountTotal = BigDecimal.ZERO;
                BigDecimal grandTotal = BigDecimal.ZERO;
                int accountTranCount = 0;
                int totalTranCount = 0;

                for (Transaction tran : transactions) {
                    Map<String, Object> enriched = reportProcessor.process(tran);

                    String cardNum = tran.getCardNum() != null ? tran.getCardNum().trim() : "";

                    // Control-break: new card number => write account total for previous
                    if (currentCard != null && !currentCard.equals(cardNum)) {
                        reportFileWriter.writeAccountTotal(currentCard, accountTotal, accountTranCount);
                        accountTotal = BigDecimal.ZERO;
                        accountTranCount = 0;
                    }
                    currentCard = cardNum;

                    // Write detail line
                    String tranDate = tran.getProcTimestamp() != null
                            ? tran.getProcTimestamp().substring(0, Math.min(10, tran.getProcTimestamp().length()))
                            : "";
                    reportFileWriter.writeDetailLine(
                            cardNum, tran.getTranId(), tran.getTranTypeCd(),
                            tran.getTranCatCd(), tran.getTranDesc(),
                            tran.getTranAmt(), tranDate);

                    BigDecimal amt = tran.getTranAmt() != null ? tran.getTranAmt() : BigDecimal.ZERO;
                    accountTotal = accountTotal.add(amt);
                    grandTotal = grandTotal.add(amt);
                    accountTranCount++;
                    totalTranCount++;
                }

                // Write final account total
                if (currentCard != null) {
                    reportFileWriter.writeAccountTotal(currentCard, accountTotal, accountTranCount);
                }

                reportFileWriter.writeGrandTotal(grandTotal, totalTranCount);

                log.info("Report generated: {} transactions, {} pages",
                        totalTranCount, reportFileWriter.getPageNumber());
            } finally {
                reportFileWriter.close();
            }

            return RepeatStatus.FINISHED;
        };
    }
}
