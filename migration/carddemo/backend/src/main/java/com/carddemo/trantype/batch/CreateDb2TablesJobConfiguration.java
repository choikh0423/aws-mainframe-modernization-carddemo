package com.carddemo.trantype.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

/**
 * Job CREADB21 — {@code jcl/CREADB21.jcl}, which creates the CARDDEMO DB2
 * database and loads the two reference tables. One {@link Step} per
 * {@code EXEC PGM=}, keeping the JCL's SET values ({@code CODER=AWS},
 * {@code LBNM=&CODER..M2.CARDDEMO}, {@code DB2S=DAZ1}) as documentation of what
 * each step addressed on z/OS:
 *
 * <ul>
 *   <li>{@code FREEPLN} — {@code CNTL(DB2FREE)} frees the CARDDEMO plan and
 *       packages, ending RC 8 when they do not exist. There are no DB2 plans or
 *       packages after boundary B-08 (JPA has no bind step), so the step logs
 *       that it is not applicable and completes (FR-C2).</li>
 *   <li>{@code CRCRDDB} — DSNTIAD running {@code CNTL(DB2CREAT)}: the database,
 *       tablespaces, tables, unique indexes, the {@code ON DELETE RESTRICT}
 *       foreign key and the GRANTs. Under D-7 that DDL is the foundation Flyway
 *       baseline ({@code db2_transaction_type},
 *       {@code db2_transaction_type_category}), so the step asserts the tables
 *       are there instead of re-creating them (FR-C3).</li>
 *   <li>{@code LDTTYPE} — IEFBR14, a no-op that only exists to carry the
 *       {@code COND=(0,NE)}.</li>
 *   <li>{@code RUNTEP2} — DSNTEP4 running {@code CNTL(DB2LTTYP)}, the seven
 *       transaction types.</li>
 *   <li>{@code LDTCCAT} — DSNTEP4 running {@code CNTL(DB2LTCAT)}, the eighteen
 *       transaction-type categories.</li>
 * </ul>
 *
 * <p>Both load members are plain {@code INSERT ... SELECT}s with no preceding
 * DELETE, so a second run against populated tables fails on the unique index —
 * that duplicate-key failure is preserved (FR-C6). The type description of
 * {@code '06'} is the source's own misspelling {@code REVERAL}
 * (ctl/DB2LTTYP.ctl:19); the foundation seed spells it {@code Reversal}, and
 * the contradiction is recorded in the stream analysis rather than corrected
 * here.
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.profiles.active=postgres --spring.batch.job.name=CREADB21
 * </pre>
 */
@Configuration
public class CreateDb2TablesJobConfiguration {

    /** {@code //   SET CODER=AWS} (CREADB21.jcl:00072022). */
    public static final String CODER = "AWS";
    /** {@code //   SET LBNM=&CODER..M2.CARDDEMO}. */
    public static final String LBNM = CODER + ".M2.CARDDEMO";
    /** {@code //   SET DB2S=DAZ1}. */
    public static final String DB2S = "DAZ1";

    /** ctl/DB2LTTYP.ctl:16-22, in the source's own order. */
    static final List<String[]> TRANSACTION_TYPES = List.of(
            new String[] {"01", "PURCHASE"},
            new String[] {"02", "PAYMENT"},
            new String[] {"03", "CREDIT"},
            new String[] {"04", "AUTHORIZATION"},
            new String[] {"05", "REFUND"},
            new String[] {"06", "REVERAL"},
            new String[] {"07", "ADJUSTMENT"});

    /** ctl/DB2LTCAT.ctl:20-37, in the source's own order. */
    static final List<String[]> TRANSACTION_TYPE_CATEGORIES = List.of(
            new String[] {"01", "0001", "REGULAR SALES DRAFT"},
            new String[] {"01", "0002", "REGULAR CASH ADVANCE"},
            new String[] {"01", "0003", "CONVENIENCE CHECK DEBIT"},
            new String[] {"01", "0004", "ATM CASH ADVANCE"},
            new String[] {"01", "0005", "INTEREST AMOUNT"},
            new String[] {"02", "0001", "CASH PAYMENT"},
            new String[] {"02", "0002", "ELECTRONIC PAYMENT"},
            new String[] {"02", "0003", "CHECK PAYMENT"},
            new String[] {"03", "0001", "CREDIT TO ACCOUNT"},
            new String[] {"03", "0002", "CREDIT TO PURCHASE BALANCE"},
            new String[] {"03", "0003", "CREDIT TO CASH BALANCE"},
            new String[] {"04", "0001", "ZERO DOLLAR AUTHORIZATION"},
            new String[] {"04", "0002", "ONLINE PURCHASE AUTHORIZATION"},
            new String[] {"04", "0003", "TRAVEL BOOKING AUTHORIZATION"},
            new String[] {"05", "0001", "REFUND CREDIT"},
            new String[] {"06", "0001", "FRAUD REVERSAL"},
            new String[] {"06", "0002", "NON FRAUD REVERSAL"},
            new String[] {"07", "0001", "SALES DRAFT CREDIT ADJUSTMENT"});

    private static final Logger log = LoggerFactory.getLogger(CreateDb2TablesJobConfiguration.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbcTemplate;

    public CreateDb2TablesJobConfiguration(JobRepository jobRepository,
                                           PlatformTransactionManager transactionManager,
                                           JdbcTemplate jdbcTemplate) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Bean
    public Job createDb2TablesJob(@Qualifier("freePlanStep") Step freePlan,
                                  @Qualifier("createDatabaseStep") Step createDatabase,
                                  @Qualifier("loadTypePrepareStep") Step loadTypePrepare,
                                  @Qualifier("loadTypeStep") Step loadType,
                                  @Qualifier("loadCategoryStep") Step loadCategory) {
        return new JobBuilder("CREADB21", jobRepository)
                .start(freePlan)
                .next(createDatabase)
                .next(loadTypePrepare)
                .next(loadType)
                .next(loadCategory)
                .build();
    }

    @Bean
    public Step freePlanStep() {
        Tasklet tasklet = (contribution, chunkContext) -> {
            log.info("FREEPLN: DB2 plans and packages are not applicable after B-08"
                    + " (subsystem {}, library {}.CNTL(DB2FREE))", DB2S, LBNM);
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder("FREEPLN", jobRepository).tasklet(tasklet, transactionManager).build();
    }

    @Bean
    public Step createDatabaseStep() {
        Tasklet tasklet = (contribution, chunkContext) -> {
            jdbcTemplate.queryForObject("select count(*) from db2_transaction_type", Integer.class);
            jdbcTemplate.queryForObject("select count(*) from db2_transaction_type_category",
                    Integer.class);
            log.info("CRCRDDB: CARDDEMO tables present (DDL supplied by the Flyway baseline)");
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder("CRCRDDB", jobRepository).tasklet(tasklet, transactionManager).build();
    }

    @Bean
    public Step loadTypePrepareStep() {
        Tasklet tasklet = (contribution, chunkContext) -> RepeatStatus.FINISHED;
        return new StepBuilder("LDTTYPE", jobRepository).tasklet(tasklet, transactionManager).build();
    }

    @Bean
    public Step loadTypeStep() {
        Tasklet tasklet = (contribution, chunkContext) -> {
            for (String[] row : TRANSACTION_TYPES) {
                jdbcTemplate.update(
                        "insert into db2_transaction_type (tr_type, tr_description) values (?, ?)",
                        row[0], row[1]);
            }
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder("RUNTEP2", jobRepository).tasklet(tasklet, transactionManager).build();
    }

    @Bean
    public Step loadCategoryStep() {
        Tasklet tasklet = (contribution, chunkContext) -> {
            for (String[] row : TRANSACTION_TYPE_CATEGORIES) {
                jdbcTemplate.update(
                        "insert into db2_transaction_type_category"
                                + " (trc_type_code, trc_type_category, trc_cat_data) values (?, ?, ?)",
                        row[0], row[1], row[2]);
            }
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder("LDTCCAT", jobRepository).tasklet(tasklet, transactionManager).build();
    }
}
