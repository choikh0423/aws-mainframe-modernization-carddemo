package com.carddemo.statements;

import com.carddemo.common.batch.AbendService;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

/**
 * Resolves CREASTMT's {@code cycle} job parameter ({@code YYYY-MM}) before any
 * step runs, so a bad value abends the job without touching the previous run's
 * reports. An omitted cycle is the current month.
 *
 * <p>The resolved cycle and the run's archive timestamp go into the job's
 * execution context for {@link StatementArchiveWriter}.
 */
@Component
public class StatementCycle implements JobExecutionListener {

    public static final String PARAMETER = "cycle";

    static final String CONTEXT_CYCLE = "statements.cycle";
    static final String CONTEXT_GENERATED_TS = "statements.generatedTs";

    private static final Pattern FORMAT = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    private final AbendService abendService;
    private final Clock clock;

    @Autowired
    public StatementCycle(AbendService abendService) {
        this(abendService, Clock.systemDefaultZone());
    }

    StatementCycle(AbendService abendService, Clock clock) {
        this.abendService = abendService;
        this.clock = clock;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        String cycle = resolve(jobExecution.getJobParameters().getString(PARAMETER));
        ExecutionContext context = jobExecution.getExecutionContext();
        context.putString(CONTEXT_CYCLE, cycle);
        context.putString(CONTEXT_GENERATED_TS,
                LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS).toString());
    }

    /** The cycle to archive under: {@code value} when valid, the current month when absent. */
    String resolve(String value) {
        if (value == null) {
            return YearMonth.now(clock).toString();
        }
        if (!FORMAT.matcher(value).matches()) {
            throw abendService.abend("0001", "CREASTMT", "CYCLE",
                    "INVALID CYCLE PARAMETER '" + value + "' - EXPECTED YYYY-MM");
        }
        return value;
    }
}
