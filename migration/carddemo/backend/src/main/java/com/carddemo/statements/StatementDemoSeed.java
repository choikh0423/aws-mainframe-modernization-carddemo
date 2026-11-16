package com.carddemo.statements;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;

/**
 * Demo seed for S-21 OnlineStatements: with {@code carddemo.statements.seed-on-startup=true}
 * (on in the {@code h2} profile) the app runs CREASTMT for the previous and the current
 * cycle while it boots, so the statement screens have data without a separate batch run.
 *
 * <p>It runs as a lifecycle phase ahead of the web server, so no request is served before
 * the archive is filled, and never when the process is itself a batch run
 * ({@code spring.batch.job.name} set).
 *
 * <p>CBSTM03A abends {@code ERROR READING CUSTFILE} on a card_xref row whose customer or
 * account is missing, which the stock seed has (the five CT02 fixture cards of
 * {@code R__seed_transaction_demo_data.sql}). Those rows are set aside for the seed's runs
 * and put back afterwards; CREASTMT itself is unchanged and still abends on them when run
 * from the command line.
 */
@Component
@ConditionalOnProperty(name = "carddemo.statements.seed-on-startup", havingValue = "true")
public class StatementDemoSeed implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(StatementDemoSeed.class);

    private static final String ORPHAN_XREFS = "SELECT card_num, cust_id, acct_id FROM card_xref x"
            + " WHERE NOT EXISTS (SELECT 1 FROM customers c WHERE c.cust_id = x.cust_id)"
            + " OR NOT EXISTS (SELECT 1 FROM accounts a WHERE a.acct_id = x.acct_id)";

    private final JobLauncher jobLauncher;
    private final Job createStatementJob;
    private final JdbcTemplate jdbcTemplate;
    private final String batchJobName;
    private final Clock clock;

    private volatile boolean running;

    @Autowired
    public StatementDemoSeed(JobLauncher jobLauncher,
                             @Qualifier("createStatementJob") Job createStatementJob,
                             JdbcTemplate jdbcTemplate,
                             @Value("${spring.batch.job.name:}") String batchJobName) {
        this(jobLauncher, createStatementJob, jdbcTemplate, batchJobName, Clock.systemDefaultZone());
    }

    StatementDemoSeed(JobLauncher jobLauncher, Job createStatementJob, JdbcTemplate jdbcTemplate,
                      String batchJobName, Clock clock) {
        this.jobLauncher = jobLauncher;
        this.createStatementJob = createStatementJob;
        this.jdbcTemplate = jdbcTemplate;
        this.batchJobName = batchJobName;
        this.clock = clock;
    }

    /** The cycles seeded: the previous month, then the current one (YYYY-MM). */
    List<String> cycles() {
        YearMonth current = YearMonth.now(clock);
        return List.of(current.minusMonths(1).toString(), current.toString());
    }

    @Override
    public void start() {
        running = true;
        if (!batchJobName.isBlank()) {
            return;
        }
        seed();
    }

    void seed() {
        List<Object[]> setAside = jdbcTemplate.query(ORPHAN_XREFS, (rs, row) -> new Object[] {
                rs.getString("card_num"), rs.getLong("cust_id"), rs.getLong("acct_id")});
        if (!setAside.isEmpty()) {
            LOG.info("statement demo seed: setting aside {} card_xref rows with no customer or account",
                    setAside.size());
            jdbcTemplate.batchUpdate("DELETE FROM card_xref WHERE card_num = ?",
                    setAside.stream().map(xref -> new Object[] {xref[0]}).toList());
        }
        try {
            for (String cycle : cycles()) {
                run(cycle);
            }
        } finally {
            if (!setAside.isEmpty()) {
                jdbcTemplate.batchUpdate(
                        "INSERT INTO card_xref (card_num, cust_id, acct_id) VALUES (?, ?, ?)", setAside);
            }
        }
    }

    private void run(String cycle) {
        try {
            JobExecution execution = jobLauncher.run(createStatementJob, new JobParametersBuilder()
                    .addString(StatementCycle.PARAMETER, cycle)
                    .addLong("statements.seed.ts", System.currentTimeMillis())
                    .toJobParameters());
            if (execution.getStatus() == BatchStatus.COMPLETED) {
                LOG.info("statement demo seed: CREASTMT cycle={} COMPLETED", cycle);
            } else {
                LOG.warn("statement demo seed: CREASTMT cycle={} ended {}: {}",
                        cycle, execution.getStatus(), execution.getAllFailureExceptions());
            }
        } catch (Exception e) {
            LOG.warn("statement demo seed: CREASTMT cycle={} could not start", cycle, e);
        }
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Ahead of the embedded web server's lifecycle phase. */
    @Override
    public int getPhase() {
        return 0;
    }
}
