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
 * Job UNLDPADB — {@code EXEC PGM=DFSRRC00,PARM='DLI,PAUDBUNL,PAUTBUNL,...'}:
 * unload the pending-authorization database to the two sequential files
 * LOADPADB reads back.
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.profiles.active=postgres --spring.batch.job.name=UNLDPADB \
 *      --outfil1=/tmp/pautdb.root.txt --outfil2=/tmp/pautdb.child.txt
 * </pre>
 *
 * <p>The JCL's {@code STEP0} {@code IEFBR14} that deletes the two output data
 * sets before the unload has no migrated counterpart: the flat-file writers
 * truncate their output.
 */
@Configuration
public class PendingAuthUnloadJobConfiguration {

    /** {@code //OUTFIL1 DD DSN=AWS.M2.CARDDEMO.PAUTDB.ROOT.FILEO}. */
    static final String DEFAULT_ROOT_FILE = "pautdb.root.txt";
    /** {@code //OUTFIL2 DD DSN=AWS.M2.CARDDEMO.PAUTDB.CHILD.FILEO}. */
    static final String DEFAULT_CHILD_FILE = "pautdb.child.txt";

    private final JobRepository jobRepository;
    private final PendingAuthUnloadSupport support;
    private final String dataDir;

    public PendingAuthUnloadJobConfiguration(JobRepository jobRepository,
                                             PendingAuthUnloadSupport support,
                                             @Value("${carddemo.batch.data-dir}") String dataDir) {
        this.jobRepository = jobRepository;
        this.support = support;
        this.dataDir = dataDir;
    }

    @Bean
    public Job pendingAuthUnloadJob(@Qualifier("unloadRootStep") Step rootStep,
                                    @Qualifier("unloadChildStep") Step childStep) {
        return new JobBuilder("UNLDPADB", jobRepository)
                .start(rootStep)
                .next(childStep)
                .build();
    }

    @Bean
    @JobScope
    public Step unloadRootStep(@Value("#{jobParameters['outfil1']}") String outfil1) {
        return support.rootUnloadStep("OUTFIL1", path(outfil1, DEFAULT_ROOT_FILE));
    }

    @Bean
    @JobScope
    public Step unloadChildStep(@Value("#{jobParameters['outfil2']}") String outfil2) {
        return support.childUnloadStep("OUTFIL2", path(outfil2, DEFAULT_CHILD_FILE));
    }

    private String path(String parameter, String defaultName) {
        return parameter == null || parameter.isBlank()
                ? dataDir + "/" + defaultName : parameter;
    }
}
