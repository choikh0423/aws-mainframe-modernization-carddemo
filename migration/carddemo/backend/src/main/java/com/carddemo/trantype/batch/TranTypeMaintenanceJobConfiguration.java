package com.carddemo.trantype.batch;

import com.carddemo.trantype.message.TranTypeMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Job MNTTRDB2 — {@code jcl/MNTTRDB2.jcl}, one step {@code STEP1}
 * ({@code EXEC PGM=IKJEFT01} running {@code RUN PROGRAM(COBTUPDT)
 * PLAN(CARDDEMO)}), which maintains the DB2 transaction-type table from the
 * INPFILE sequential file.
 *
 * <p>{@code COBTUPDT.cbl} is a read-loop, not a chunk: {@code 9999-ABEND}
 * displays the reason and moves 4 to RETURN-CODE but does <em>not</em> stop the
 * run, so a bad record neither rolls the earlier records back nor prevents the
 * later ones from being applied (FR-B7). A tasklet reproduces that; the warning
 * shows up as the step's exit status {@code RC=4} rather than a failed job,
 * exactly as the JCL saw condition code 4.
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.profiles.active=postgres --spring.batch.job.name=MNTTRDB2 \
 *      --inpfile=/path/to/INPFILE
 * </pre>
 */
@Configuration
public class TranTypeMaintenanceJobConfiguration {

    /** The record the FD describes: 1 + 2 + 50 (MNTTRDB2.jcl:11-18). */
    public static final int RECORD_LENGTH = 53;
    /** {@code MOVE 4 TO RETURN-CODE} in {@code 9999-ABEND} (COBTUPDT.cbl:232). */
    public static final ExitStatus RC_4 = new ExitStatus("RC=4");

    private static final Logger log = LoggerFactory.getLogger(TranTypeMaintenanceJobConfiguration.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbcTemplate;

    public TranTypeMaintenanceJobConfiguration(JobRepository jobRepository,
                                               PlatformTransactionManager transactionManager,
                                               JdbcTemplate jdbcTemplate) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Bean
    public Job tranTypeMaintenanceJob(@Qualifier("tranTypeMaintenanceStep") Step step) {
        return new JobBuilder("MNTTRDB2", jobRepository)
                .start(step)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet tranTypeMaintenanceTasklet(@Value("#{jobParameters['inpfile']}") String inpfile) {
        return (contribution, chunkContext) -> {
            Path input = Path.of(inpfile);
            log.info("OPEN FILE OK");
            boolean abended = false;
            for (String line : readLines(input)) {
                log.info("PROCESSING   {}", pad(line));
                abended |= treatRecord(pad(line));
            }
            if (abended) {
                contribution.setExitStatus(RC_4);
            }
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step tranTypeMaintenanceStep(@Qualifier("tranTypeMaintenanceTasklet") Tasklet tranTypeMaintenanceTasklet) {
        return new StepBuilder("STEP1", jobRepository)
                .tasklet(tranTypeMaintenanceTasklet, transactionManager)
                .build();
    }

    /** {@code 1003-TREAT-RECORD} (COBTUPDT.cbl:109-130). */
    private boolean treatRecord(String record) {
        String operation = record.substring(0, 1);
        String typeCode = record.substring(1, 3);
        String description = record.substring(3, RECORD_LENGTH).stripTrailing();

        switch (operation) {
            case "A" -> {
                log.info(TranTypeMessages.BATCH_ADDING);
                return insert(typeCode, description);
            }
            case "U" -> {
                log.info(TranTypeMessages.BATCH_UPDATING);
                return update(typeCode, description);
            }
            case "D" -> {
                log.info(TranTypeMessages.BATCH_DELETING);
                return delete(typeCode);
            }
            case "*" -> {
                log.info(TranTypeMessages.BATCH_IGNORING_COMMENT);
                return false;
            }
            default -> {
                return abend(TranTypeMessages.BATCH_TYPE_NOT_VALID);
            }
        }
    }

    /** {@code 10031-INSERT-DB} (COBTUPDT.cbl:132-164). */
    private boolean insert(String typeCode, String description) {
        try {
            jdbcTemplate.update(
                    "insert into db2_transaction_type (tr_type, tr_description) values (?, ?)",
                    typeCode, description);
        } catch (DuplicateKeyException e) {
            // SQLCODE -803: the unique index on TR_TYPE rejected the row.
            return abend(TranTypeMessages.batchSqlError(-803));
        } catch (DataIntegrityViolationException e) {
            return abend(TranTypeMessages.batchSqlError(-530));
        }
        log.info(TranTypeMessages.BATCH_INSERTED);
        return false;
    }

    /** {@code 10032-UPDATE-DB} (COBTUPDT.cbl:166-195). */
    private boolean update(String typeCode, String description) {
        int updated = jdbcTemplate.update(
                "update db2_transaction_type set tr_description = ? where tr_type = ?",
                description, typeCode);
        if (updated == 0) {
            // SQLCODE +100.
            return abend(TranTypeMessages.BATCH_NO_RECORDS);
        }
        log.info(TranTypeMessages.BATCH_UPDATED);
        return false;
    }

    /** {@code 10033-DELETE-DB} (COBTUPDT.cbl:196-226). */
    private boolean delete(String typeCode) {
        int deleted;
        try {
            deleted = jdbcTemplate.update("delete from db2_transaction_type where tr_type = ?", typeCode);
        } catch (DataIntegrityViolationException e) {
            // SQLCODE -532: TRANSACTION_TYPE_CATEGORY still references the row.
            return abend(TranTypeMessages.batchSqlError(-532));
        }
        if (deleted == 0) {
            return abend(TranTypeMessages.BATCH_NO_RECORDS);
        }
        log.info(TranTypeMessages.BATCH_DELETED);
        return false;
    }

    /** {@code 9999-ABEND} (COBTUPDT.cbl:230-233): display, RC=4, keep reading. */
    private boolean abend(String message) {
        log.error(message);
        return true;
    }

    private static List<String> readLines(Path input) {
        try {
            return Files.readAllLines(input, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** RECFM=F: a short line is a short record padded with spaces. */
    private static String pad(String line) {
        if (line.length() >= RECORD_LENGTH) {
            return line.substring(0, RECORD_LENGTH);
        }
        return line + " ".repeat(RECORD_LENGTH - line.length());
    }
}
