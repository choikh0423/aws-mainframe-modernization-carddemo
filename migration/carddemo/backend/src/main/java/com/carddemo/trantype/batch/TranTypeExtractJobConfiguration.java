package com.carddemo.trantype.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Job TRANEXTR — {@code jcl/TRANEXTR.jcl}, the daily unload of the two DB2
 * reference tables that Transaction Reporting reads. One {@link Step} per
 * {@code EXEC PGM=}:
 *
 * <ul>
 *   <li>{@code STEP10} IEBGENER — copy {@code TRANTYPE.PS} to the backup GDG;</li>
 *   <li>{@code STEP20} IEBGENER — copy {@code TRANCATG.PS} to its backup GDG;</li>
 *   <li>{@code STEP30} IEFBR14 — delete both PS datasets from the previous run;</li>
 *   <li>{@code STEP40} DSNTIAUL — unload TRANSACTION_TYPE, 60-byte records;</li>
 *   <li>{@code STEP50} DSNTIAUL — unload TRANSACTION_TYPE_CATEGORY, 60-byte records.</li>
 * </ul>
 *
 * <p>The DSNTIAUL SYSIN in the JCL casts the concatenation to {@code CHAR(60)}:
 * {@code TR_TYPE || CHAR(TR_DESCRIPTION,50) || REPEAT('0',8)} for the types and
 * {@code TRC_TYPE_CODE || TRC_TYPE_CATEGORY || CHAR(TRC_CAT_DATA,50) ||
 * REPEAT('0',4)} for the categories, each ordered by its key — reproduced here
 * byte for byte, including the space padding {@code CAST(... AS CHAR(50))}
 * applies to the VARCHAR columns.
 *
 * <p>The GDG is a directory of generations: {@code TRANTYPE.BKUP.G0001V00},
 * {@code G0002V00}, … {@code (+1)} being the next unused number. As on z/OS,
 * STEP10 fails when the PS dataset from the previous run is missing, so the
 * first run of a fresh installation ends on a JCL error (FR-X8).
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.profiles.active=postgres --spring.batch.job.name=TRANEXTR \
 *      --hlq=/path/to/AWS.M2.CARDDEMO
 * </pre>
 */
@Configuration
public class TranTypeExtractJobConfiguration {

    /** LRECL=60 on both SYSUT2/SYSREC00 DDs. */
    public static final int RECORD_LENGTH = 60;

    public static final String TRANTYPE_PS = "TRANTYPE.PS";
    public static final String TRANCATG_PS = "TRANCATG.PS";
    public static final String TRANTYPE_BKUP = "TRANTYPE.BKUP";
    public static final String TRANCATG_BKUP = "TRANCATG.PS.BKUP";

    private static final Logger log = LoggerFactory.getLogger(TranTypeExtractJobConfiguration.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbcTemplate;

    public TranTypeExtractJobConfiguration(JobRepository jobRepository,
                                           PlatformTransactionManager transactionManager,
                                           JdbcTemplate jdbcTemplate) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Bean
    public Job tranTypeExtractJob(@Qualifier("extractBackupTypeStep") Step step10,
                                  @Qualifier("extractBackupCategoryStep") Step step20,
                                  @Qualifier("extractDeleteStep") Step step30,
                                  @Qualifier("extractTypeStep") Step step40,
                                  @Qualifier("extractCategoryStep") Step step50) {
        return new JobBuilder("TRANEXTR", jobRepository)
                .start(step10)
                .next(step20)
                .next(step30)
                .next(step40)
                .next(step50)
                .build();
    }

    // ------------------------------------------------------------- STEP10 / STEP20

    @Bean
    @StepScope
    public Tasklet extractBackupTypeTasklet(@Value("#{jobParameters['hlq']}") String hlq) {
        return (contribution, chunkContext) -> backup(Path.of(hlq), TRANTYPE_PS, TRANTYPE_BKUP);
    }

    @Bean
    public Step extractBackupTypeStep(@Qualifier("extractBackupTypeTasklet") Tasklet tasklet) {
        return new StepBuilder("STEP10", jobRepository).tasklet(tasklet, transactionManager).build();
    }

    @Bean
    @StepScope
    public Tasklet extractBackupCategoryTasklet(@Value("#{jobParameters['hlq']}") String hlq) {
        return (contribution, chunkContext) -> backup(Path.of(hlq), TRANCATG_PS, TRANCATG_BKUP);
    }

    @Bean
    public Step extractBackupCategoryStep(@Qualifier("extractBackupCategoryTasklet") Tasklet tasklet) {
        return new StepBuilder("STEP20", jobRepository).tasklet(tasklet, transactionManager).build();
    }

    // ------------------------------------------------------------------- STEP30

    @Bean
    @StepScope
    public Tasklet extractDeleteTasklet(@Value("#{jobParameters['hlq']}") String hlq) {
        return (contribution, chunkContext) -> {
            Path dir = Path.of(hlq);
            deleteIfPresent(dir.resolve(TRANTYPE_PS));
            deleteIfPresent(dir.resolve(TRANCATG_PS));
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step extractDeleteStep(@Qualifier("extractDeleteTasklet") Tasklet tasklet) {
        return new StepBuilder("STEP30", jobRepository).tasklet(tasklet, transactionManager).build();
    }

    // ------------------------------------------------------------- STEP40 / STEP50

    @Bean
    @StepScope
    public Tasklet extractTypeTasklet(@Value("#{jobParameters['hlq']}") String hlq) {
        return (contribution, chunkContext) -> {
            List<String> records = jdbcTemplate.query(
                    "select tr_type, tr_description from db2_transaction_type order by tr_type",
                    (rs, rowNum) -> fixed(rs.getString(1), 2)
                            + fixed(rs.getString(2), 50)
                            + "0".repeat(8));
            write(Path.of(hlq).resolve(TRANTYPE_PS), records);
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step extractTypeStep(@Qualifier("extractTypeTasklet") Tasklet tasklet) {
        return new StepBuilder("STEP40", jobRepository).tasklet(tasklet, transactionManager).build();
    }

    @Bean
    @StepScope
    public Tasklet extractCategoryTasklet(@Value("#{jobParameters['hlq']}") String hlq) {
        return (contribution, chunkContext) -> {
            List<String> records = jdbcTemplate.query(
                    "select trc_type_code, trc_type_category, trc_cat_data"
                            + " from db2_transaction_type_category"
                            + " order by trc_type_code, trc_type_category",
                    (rs, rowNum) -> fixed(rs.getString(1), 2)
                            + fixed(rs.getString(2), 4)
                            + fixed(rs.getString(3), 50)
                            + "0".repeat(4));
            write(Path.of(hlq).resolve(TRANCATG_PS), records);
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step extractCategoryStep(@Qualifier("extractCategoryTasklet") Tasklet tasklet) {
        return new StepBuilder("STEP50", jobRepository).tasklet(tasklet, transactionManager).build();
    }

    // ------------------------------------------------------------------- helpers

    /** IEBGENER SYSUT1 -> SYSUT2 with {@code DSN=...BKUP(+1)}. */
    private RepeatStatus backup(Path dir, String source, String gdgBase) {
        Path input = dir.resolve(source);
        if (!Files.exists(input)) {
            throw new IllegalStateException("IEBGENER: SYSUT1 dataset not found: " + input);
        }
        Path generation = dir.resolve(nextGeneration(dir, gdgBase));
        try {
            Files.copy(input, generation);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        log.info("IEBGENER copied {} to {}", input.getFileName(), generation.getFileName());
        return RepeatStatus.FINISHED;
    }

    /** {@code (+1)}: the highest catalogued generation plus one. */
    private static String nextGeneration(Path dir, String gdgBase) {
        String prefix = gdgBase + ".G";
        int highest = 0;
        try (Stream<Path> entries = Files.list(dir)) {
            highest = entries.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(prefix) && name.endsWith("V00"))
                    .map(name -> name.substring(prefix.length(), name.length() - 3))
                    .filter(digits -> digits.chars().allMatch(Character::isDigit))
                    .mapToInt(Integer::parseInt)
                    .max()
                    .orElse(0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return String.format(Locale.ROOT, "%s.G%04dV00", gdgBase, highest + 1);
    }

    /** IEFBR14 with {@code DISP=(MOD,DELETE,DELETE)}: gone if it was there. */
    private static void deleteIfPresent(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path path, List<String> records) {
        try {
            Files.write(path, records, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code CAST(column AS CHAR(n))}: space padded, never truncated short. */
    private static String fixed(String value, int length) {
        String text = value == null ? "" : value;
        if (text.length() >= length) {
            return text.substring(0, length);
        }
        return text + " ".repeat(length - text.length());
    }
}
