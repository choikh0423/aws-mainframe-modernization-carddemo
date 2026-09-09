package com.carddemo.batch;

import com.carddemo.common.batch.AbendException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The only way a Spring Batch job starts in this application.
 *
 * <p>Boot's own {@code JobLauncherApplicationRunner} is switched off
 * ({@code spring.batch.job.enabled=false}) because it runs every job in the
 * context on startup, which would fire the whole estate's batch every time the
 * online app boots. This runner launches exactly the job named by
 * {@code spring.batch.job.name} and does nothing at all when that property is
 * absent, so the online app boots clean.
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.profiles.active=postgres --spring.batch.job.name=DATALOAD
 * </pre>
 *
 * <p>Any remaining {@code --key=value} arguments become JobParameters, mirroring
 * the JCL PARM= and SYSIN values the COBOL programs read. The process exit code
 * follows the JCL condition-code convention the operations chain expects: 0 for
 * a completed job, 12 for anything else, including an
 * {@link AbendException} raised through
 * {@link com.carddemo.common.batch.AbendService}.
 */
@Component
public class BatchJobLauncher implements ApplicationRunner, ExitCodeGenerator {

    /** COND CODE 12: the value CardDemo's JCL treats as a failed step. */
    public static final int EXIT_CODE_FAILED = 12;

    private static final Logger log = LoggerFactory.getLogger(BatchJobLauncher.class);

    private static final List<String> RESERVED_ARGS = List.of(
            "spring.batch.job.name", "spring.profiles.active", "spring.main.web-application-type");

    private final JobLauncher jobLauncher;
    private final List<Job> jobs;
    private final String jobName;

    private int exitCode;

    public BatchJobLauncher(JobLauncher jobLauncher,
                            List<Job> jobs,
                            @Value("${spring.batch.job.name:}") String jobName) {
        this.jobLauncher = jobLauncher;
        this.jobs = jobs;
        this.jobName = jobName;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (jobName.isBlank()) {
            return;
        }
        Job job = jobs.stream()
                .filter(candidate -> candidate.getName().equals(jobName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No batch job named '" + jobName + "'. Known jobs: "
                                + jobs.stream().map(Job::getName).sorted().toList()));

        JobExecution execution = jobLauncher.run(job, toJobParameters(args));
        if (execution.getStatus() != BatchStatus.COMPLETED) {
            exitCode = EXIT_CODE_FAILED;
            log.error("Job {} ended with status {}: {}",
                    jobName, execution.getStatus(), execution.getAllFailureExceptions());
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    private JobParameters toJobParameters(ApplicationArguments args) {
        JobParametersBuilder builder = new JobParametersBuilder();
        for (String name : args.getOptionNames()) {
            if (RESERVED_ARGS.contains(name) || name.startsWith("spring.")) {
                continue;
            }
            List<String> values = args.getOptionValues(name);
            builder.addString(name, values.isEmpty() ? "" : values.get(0));
        }
        return builder.toJobParameters();
    }
}
