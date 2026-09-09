package com.carddemo.batch.authpurge;

import com.carddemo.common.domain.PendingAuthDetailRecord;
import com.carddemo.common.domain.PendingAuthSummaryRecord;
import com.carddemo.common.repository.PendingAuthDetailRepository;
import com.carddemo.common.repository.PendingAuthSummaryRepository;
import com.carddemo.pendingauth.repository.PendingAuthDetailBrowseRepository;
import com.carddemo.pendingauth.repository.PendingAuthSummaryBrowseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Job CBPAUP0J — {@code EXEC PGM=DFSRRC00,PARM='BMP,CBPAUP0C,PSBPAUTB'}: delete
 * expired pending authorizations.
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.profiles.active=postgres --spring.batch.job.name=CBPAUP0J \
 *      --expiryDays=05 --chkpFreq=5
 * </pre>
 *
 * <p>The BMP's {@code SYSIN} PARM ({@code PRM-INFO}) becomes job parameters with
 * the same defaults the program applies when the values are missing or
 * non-numeric: 5 expiry days and a checkpoint every 5 summaries. The IMS
 * {@code CHKP} call is the chunk commit — chunk size is the checkpoint
 * frequency — so a restart resumes at the last committed chunk through the
 * shared JobRepository instead of the last checkpoint id.
 */
@Configuration
public class PendingAuthPurgeJobConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PendingAuthPurgeJobConfiguration.class);

    /** {@code IF P-EXPIRY-DAYS IS NUMERIC ... ELSE MOVE 5} (CBPAUP0C.cbl:196-200). */
    static final int DEFAULT_EXPIRY_DAYS = 5;
    /** {@code IF P-CHKP-FREQ = SPACES OR 0 OR LOW-VALUES MOVE 5} (CBPAUP0C.cbl:201-203). */
    static final int DEFAULT_CHKP_FREQ = 5;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final PendingAuthSummaryBrowseRepository summaryBrowseRepository;
    private final PendingAuthDetailBrowseRepository detailBrowseRepository;
    private final PendingAuthSummaryRepository summaryRepository;
    private final PendingAuthDetailRepository detailRepository;
    private final Clock clock;

    public PendingAuthPurgeJobConfiguration(JobRepository jobRepository,
                                            PlatformTransactionManager transactionManager,
                                            PendingAuthSummaryBrowseRepository summaryBrowseRepository,
                                            PendingAuthDetailBrowseRepository detailBrowseRepository,
                                            PendingAuthSummaryRepository summaryRepository,
                                            PendingAuthDetailRepository detailRepository,
                                            Clock clock) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.summaryBrowseRepository = summaryBrowseRepository;
        this.detailBrowseRepository = detailBrowseRepository;
        this.summaryRepository = summaryRepository;
        this.detailRepository = detailRepository;
        this.clock = clock;
    }

    /** What one root contributes to the deletes and to the end-of-run totals. */
    record PurgeItem(PendingAuthSummaryRecord summary,
                     List<PendingAuthDetailRecord> expiredDetails,
                     boolean deleteSummary,
                     int detailsRead) {
    }

    @Bean
    public Job pendingAuthPurgeJob(Step purgeStep) {
        return new JobBuilder("CBPAUP0J", jobRepository)
                .start(purgeStep)
                .build();
    }

    @Bean
    @JobScope
    public Step purgeStep(@Value("#{jobParameters['expiryDays']}") String expiryDaysParm,
                          @Value("#{jobParameters['chkpFreq']}") String chkpFreqParm) {
        int expiryDays = intParm(expiryDaysParm, DEFAULT_EXPIRY_DAYS);
        int chunkSize = intParm(chkpFreqParm, DEFAULT_CHKP_FREQ);
        TotalsListener totals = new TotalsListener();
        return new StepBuilder("STEP01", jobRepository)
                .<PendingAuthSummaryRecord, PurgeItem>chunk(chunkSize, transactionManager)
                .reader(new PendingAuthSummaryReader(summaryBrowseRepository))
                .processor(processor(expiryDays))
                .writer(writer())
                .listener((ItemWriteListener<PurgeItem>) totals)
                .listener((StepExecutionListener) totals)
                .build();
    }

    /** 3000-FIND-NEXT-AUTH-DTL + 4000-CHECK-IF-EXPIRED for one root. */
    private ItemProcessor<PendingAuthSummaryRecord, PurgeItem> processor(int expiryDays) {
        return summary -> {
            List<PendingAuthDetailRecord> children =
                    detailBrowseRepository.browseChildren(summary.getPaAcctId());
            PendingAuthPurgeService.Outcome outcome = PendingAuthPurgeService.purge(
                    summary, children, currentYyddd(), expiryDays);
            return new PurgeItem(summary, outcome.expiredDetails(), outcome.deleteSummary(),
                    children.size());
        };
    }

    /**
     * 5000-DELETE-AUTH-DTL and 6000-DELETE-AUTH-SUMMARY. {@code DLET} of a root
     * segment removes its whole parentage in IMS, so any child the expiry test
     * spared goes with the root here too (the relational parent/child tables
     * carry the same dependency as a foreign key).
     */
    private ItemWriter<PurgeItem> writer() {
        return items -> {
            for (PurgeItem item : items) {
                detailRepository.deleteAll(item.expiredDetails());
                if (item.deleteSummary()) {
                    detailRepository.deleteAll(
                            detailBrowseRepository.browseChildren(item.summary().getPaAcctId()));
                    summaryRepository.delete(item.summary());
                }
            }
        };
    }

    /** {@code ACCEPT CURRENT-YYDDD FROM DAY}. */
    private int currentYyddd() {
        LocalDate today = LocalDate.now(clock);
        return (today.getYear() % 100) * 1000 + today.getDayOfYear();
    }

    private static int intParm(String value, int fallback) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty() || !v.chars().allMatch(Character::isDigit) || Integer.parseInt(v) == 0) {
            return fallback;
        }
        return Integer.parseInt(v);
    }

    /** The end-of-run {@code DISPLAY} block (CBPAUP0C.cbl:171-177). */
    private static class TotalsListener implements ItemWriteListener<PurgeItem>, StepExecutionListener {

        private int summaryRead;
        private int summaryDeleted;
        private int detailRead;
        private int detailDeleted;

        @Override
        public void afterWrite(Chunk<? extends PurgeItem> items) {
            for (PurgeItem item : items) {
                summaryRead++;
                detailRead += item.detailsRead();
                detailDeleted += item.expiredDetails().size();
                if (item.deleteSummary()) {
                    summaryDeleted++;
                }
            }
        }

        @Override
        public ExitStatus afterStep(StepExecution stepExecution) {
            log.info("*-------------------------------------*");
            log.info("# TOTAL SUMMARY READ  :{}", summaryRead);
            log.info("# SUMMARY REC DELETED :{}", summaryDeleted);
            log.info("# TOTAL DETAILS READ  :{}", detailRead);
            log.info("# DETAILS REC DELETED :{}", detailDeleted);
            log.info("*-------------------------------------*");
            stepExecution.getExecutionContext().putInt("summaryRead", summaryRead);
            stepExecution.getExecutionContext().putInt("summaryDeleted", summaryDeleted);
            stepExecution.getExecutionContext().putInt("detailRead", detailRead);
            stepExecution.getExecutionContext().putInt("detailDeleted", detailDeleted);
            return stepExecution.getExitStatus();
        }
    }
}
