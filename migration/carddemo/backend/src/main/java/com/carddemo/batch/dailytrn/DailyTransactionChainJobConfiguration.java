package com.carddemo.batch.dailytrn;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.JobParametersValidator;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Job DAILYTRN - the daily transaction batch chain the mainframe schedulers ran
 * as separate jobs, as one Spring Batch job whose steps launch the migrated jobs
 * in order:
 *
 * <pre>
 * TRANBKP → CBPAUP0J → POSTTRAN → WAITSTEP
 * </pre>
 *
 * <p>The two scheduler exports disagree on this chain. Control-M
 * ({@code CardDemo.controlm}, {@code DAILY-TransactionBackup}) runs
 * {@code CLOSEFIL → TRANBKP → WAITSTEP → OPENFIL}; CA-7 ({@code CardDemo.ca7},
 * SCHID 030) runs {@code CLOSEFIL → CBPAUP0J → POSTTRAN → WAITSTEP → OPENFIL}.
 * DAILYTRN is the union of the two, backup first so the master POSTTRAN replaces
 * is saved before it is emptied. {@code CLOSEFIL}/{@code OPENFIL} are left out
 * by decision B-15 (one shared database, no CICS files to quiesce).
 *
 * <p>Each step is a {@link org.springframework.batch.core.step.job.JobStep}, so
 * every child keeps its own job name, its own job repository history and its own
 * restart state, exactly as when it is launched alone. The chain parameters are
 * passed to every child unchanged:
 * <ul>
 *   <li>{@code runDate} (required, {@code yyyy-MM-dd}) - the business date. It
 *       makes each day one DAILYTRN instance and one instance of each child.</li>
 *   <li>{@code expiryDays}, {@code chkpFreq} - CBPAUP0J's PARM fields.</li>
 *   <li>{@code sysin} - WAITSTEP's control card.</li>
 * </ul>
 *
 * <p>A child that fails stops the chain (the launcher exits 12, the chain's
 * {@code COND} on every downstream job). Launching DAILYTRN again with the same
 * parameters restarts it: the completed children are skipped and the failed one
 * is restarted from its own checkpoint. A business date that already completed
 * is refused by the job repository. POSTTRAN's condition code 4 for rejects is
 * still the process exit code.
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.profiles.active=postgres --spring.batch.job.name=DAILYTRN \
 *      --runDate=2022-06-15
 * </pre>
 */
@Configuration
public class DailyTransactionChainJobConfiguration {

    public static final String JOB_NAME = "DAILYTRN";

    /** The business date of the run. */
    public static final String RUN_DATE_PARAMETER = "runDate";

    private final JobRepository jobRepository;
    private final JobLauncher jobLauncher;

    public DailyTransactionChainJobConfiguration(JobRepository jobRepository, JobLauncher jobLauncher) {
        this.jobRepository = jobRepository;
        this.jobLauncher = jobLauncher;
    }

    @Bean
    public Job dailyTransactionChainJob(@Qualifier("transactionBackupJob") Job tranbkp,
                                        @Qualifier("pendingAuthPurgeJob") Job cbpaup0j,
                                        @Qualifier("postTranJob") Job posttran,
                                        @Qualifier("waitStepJob") Job waitstep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(runDateRequired())
                .start(launch(tranbkp))
                .next(launch(cbpaup0j))
                .next(launch(posttran))
                .next(launch(waitstep))
                .build();
    }

    /** One chain step per child job, named after it. */
    private Step launch(Job child) {
        return new StepBuilder(child.getName(), jobRepository)
                .job(child)
                .launcher(jobLauncher)
                .build();
    }

    static JobParametersValidator runDateRequired() {
        return (JobParameters parameters) -> {
            String runDate = parameters == null ? null : parameters.getString(RUN_DATE_PARAMETER);
            if (runDate == null || runDate.isBlank()) {
                throw new JobParametersInvalidException(
                        JOB_NAME + " needs the business date: --" + RUN_DATE_PARAMETER + "=yyyy-MM-dd");
            }
            try {
                LocalDate.parse(runDate);
            } catch (DateTimeParseException e) {
                throw new JobParametersInvalidException(
                        JOB_NAME + " " + RUN_DATE_PARAMETER + " is not a yyyy-MM-dd date: " + runDate);
            }
        };
    }
}
