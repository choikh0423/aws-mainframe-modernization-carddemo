package com.carddemo.batch.operations;

import com.carddemo.common.batch.AbendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Job WAITSTEP - the timed wait of app/jcl/WAITSTEP.jcl, whose single step
 * {@code //WAIT EXEC PGM=COBSWAIT} (WAITSTEP.jcl:22) runs COBSWAIT, which calls
 * the Assembler interval timer MVSWAIT (boundary B-04).
 *
 * <p>The wait exists because the mainframe chains quiesce the CICS files before
 * a batch job and re-open them afterwards: WAITSTEP gives CICS time to complete
 * the CLOSE before the next job touches the datasets (see the Control-M and CA-7
 * graphs in docs/migration/streams/OperationsChain/). Nothing is quiesced in the
 * target - one shared database, boundary B-15 - so the wait carries no business
 * behaviour; it survives only because both scheduler graphs name a WAITSTEP job
 * between the other streams' jobs, and dropping it would make the migrated
 * chains unrunnable as documented. The wait itself is a scheduler concern and
 * should be expressed as a scheduler delay once the chains are scheduled.
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.profiles.active=postgres --spring.batch.job.name=WAITSTEP \
 *      --sysin=00003600
 * </pre>
 *
 * <p>Without {@code --sysin} the packaged control card {@code ctl/WAITSTEP.sysin}
 * is used, which is the in-stream SYSIN of WAITSTEP.jcl verbatim (36 seconds).
 */
@Configuration
public class WaitStepJobConfiguration {

    /** Job parameter carrying the SYSIN card, as the JCL supplied it in-stream. */
    public static final String SYSIN_PARAMETER = "sysin";

    /** The in-stream SYSIN of app/jcl/WAITSTEP.jcl, packaged as a control member. */
    public static final String SYSIN_RESOURCE = "ctl/WAITSTEP.sysin";

    private static final Logger log = LoggerFactory.getLogger(WaitStepJobConfiguration.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final AbendService abendService;

    public WaitStepJobConfiguration(JobRepository jobRepository,
                                    PlatformTransactionManager transactionManager,
                                    AbendService abendService) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.abendService = abendService;
    }

    @Bean
    public Job waitStepJob() {
        return new JobBuilder("WAITSTEP", jobRepository)
                .start(waitStep())
                .build();
    }

    /** WAIT - the one step of WAITSTEP.jcl, PGM=COBSWAIT. */
    @Bean
    public Step waitStep() {
        Tasklet tasklet = (contribution, chunkContext) -> {
            Object parameter = chunkContext.getStepContext().getJobParameters().get(SYSIN_PARAMETER);
            String sysin = parameter == null ? packagedSysin() : parameter.toString();
            WaitControlCard card = readCard(sysin);
            log.info("COBSWAIT waiting {} centiseconds", card.value());
            sleep(card);
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder("WAIT", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    private WaitControlCard readCard(String sysin) {
        try {
            return WaitControlCard.parse(sysin);
        } catch (IllegalArgumentException e) {
            throw abendService.abend("0001", "COBSWAIT", e.getMessage(),
                    "MOVE OF A NON-NUMERIC SYSIN VALUE TO MVSWAIT-TIME");
        }
    }

    private void sleep(WaitControlCard card) {
        try {
            Thread.sleep(card.millis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw abendService.abend("0001", "COBSWAIT", "WAIT INTERRUPTED",
                    "MVSWAIT INTERVAL TIMER DID NOT RUN TO COMPLETION");
        }
    }

    private String packagedSysin() {
        try (InputStream in = new ClassPathResource(SYSIN_RESOURCE).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Missing control member " + SYSIN_RESOURCE, e);
        }
    }
}
