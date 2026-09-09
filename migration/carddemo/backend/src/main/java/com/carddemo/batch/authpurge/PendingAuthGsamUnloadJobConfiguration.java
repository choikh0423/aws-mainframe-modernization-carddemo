package com.carddemo.batch.authpurge;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Job UNLDGSAM — {@code EXEC PGM=DFSRRC00,PARM='DLI,DBUNLDGS,DLIGSAMP,...'}:
 * the same unload as UNLDPADB, but the legacy program writes its two output
 * streams through GSAM PCBs ({@code PASFILOP} for the roots, {@code PADFILOP}
 * for the children) instead of COBOL {@code WRITE}.
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.profiles.active=postgres --spring.batch.job.name=UNLDGSAM \
 *      --pasfilop=/tmp/pautdb.root.gsam --padfilop=/tmp/pautdb.child.gsam
 * </pre>
 */
@Configuration
public class PendingAuthGsamUnloadJobConfiguration {

    /** {@code //PASFILOP DD DSN=AWS.M2.CARDDEMO.PAUTDB.ROOT.GSAM}. */
    static final String DEFAULT_ROOT_FILE = "pautdb.root.gsam.txt";
    /** {@code //PADFILOP DD DSN=AWS.M2.CARDDEMO.PAUTDB.CHILD.GSAM}. */
    static final String DEFAULT_CHILD_FILE = "pautdb.child.gsam.txt";

    private final JobRepository jobRepository;
    private final PendingAuthUnloadSupport support;
    private final String dataDir;

    public PendingAuthGsamUnloadJobConfiguration(JobRepository jobRepository,
                                                 PendingAuthUnloadSupport support,
                                                 @Value("${carddemo.batch.data-dir}") String dataDir) {
        this.jobRepository = jobRepository;
        this.support = support;
        this.dataDir = dataDir;
    }

    @Bean
    public Job pendingAuthGsamUnloadJob(@Qualifier("gsamUnloadRootStep") Step rootStep,
                                        @Qualifier("gsamUnloadChildStep") Step childStep) {
        return new JobBuilder("UNLDGSAM", jobRepository)
                .start(rootStep)
                .next(childStep)
                .build();
    }

    @Bean
    @JobScope
    public Step gsamUnloadRootStep(@Value("#{jobParameters['pasfilop']}") String pasfilop) {
        return support.rootUnloadStep("PASFILOP", path(pasfilop, DEFAULT_ROOT_FILE));
    }

    @Bean
    @JobScope
    public Step gsamUnloadChildStep(@Value("#{jobParameters['padfilop']}") String padfilop) {
        return support.childUnloadStep("PADFILOP", path(padfilop, DEFAULT_CHILD_FILE));
    }

    private String path(String parameter, String defaultName) {
        return parameter == null || parameter.isBlank()
                ? dataDir + "/" + defaultName : parameter;
    }
}
