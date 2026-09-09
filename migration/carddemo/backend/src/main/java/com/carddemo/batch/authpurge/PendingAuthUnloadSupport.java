package com.carddemo.batch.authpurge;

import com.carddemo.common.domain.PendingAuthDetailRecord;
import com.carddemo.common.domain.PendingAuthSummaryRecord;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.transform.PassThroughLineAggregator;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The steps shared by the two unload jobs, UNLDPADB (PAUDBUNL, BSAM output) and
 * UNLDGSAM (DBUNLDGS, GSAM output).
 *
 * <p>Both programs run the same traversal — {@code GN PAUTSUM0} writing the root
 * record, then {@code GNP PAUTDTL1} until {@code GE} writing the child records —
 * and differ only in how the two output streams are defined: PAUDBUNL writes
 * them as ordinary QSAM files ({@code OUTFIL1}/{@code OUTFIL2}), DBUNLDGS writes
 * them through GSAM PCBs ({@code PASFILOP}/{@code PADFILOP}). In the migrated
 * form that distinction disappears — both write flat files — so the two jobs
 * share these steps and differ only in step names and output paths (boundary
 * decision BD-7).
 *
 * <p>The legacy programs interleave the streams (a root, then its children);
 * here each stream is its own step, and because both are ordered by account id
 * the two files hold exactly the same records in the same order.
 */
@Component
public class PendingAuthUnloadSupport {

    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;

    public PendingAuthUnloadSupport(JobRepository jobRepository,
                                    PlatformTransactionManager transactionManager,
                                    EntityManagerFactory entityManagerFactory) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.entityManagerFactory = entityManagerFactory;
    }

    /** 2000-FIND-NEXT-AUTH-SUMMARY: the root stream. */
    public Step rootUnloadStep(String stepName, String outputPath) {
        ItemReader<PendingAuthSummaryRecord> reader =
                new JpaPagingItemReaderBuilder<PendingAuthSummaryRecord>()
                        .name(stepName + "Reader")
                        .entityManagerFactory(entityManagerFactory)
                        .queryString("select s from PendingAuthSummaryRecord s"
                                + " order by s.paAcctId asc")
                        .pageSize(CHUNK_SIZE)
                        .build();
        ItemProcessor<PendingAuthSummaryRecord, String> processor =
                PendingAuthSegmentFormat::formatRoot;
        return new StepBuilder(stepName, jobRepository)
                .<PendingAuthSummaryRecord, String>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(lineWriter(stepName, outputPath))
                .build();
    }

    /** 3000-FIND-NEXT-AUTH-DTL: the child stream, root key first. */
    public Step childUnloadStep(String stepName, String outputPath) {
        ItemReader<PendingAuthDetailRecord> reader =
                new JpaPagingItemReaderBuilder<PendingAuthDetailRecord>()
                        .name(stepName + "Reader")
                        .entityManagerFactory(entityManagerFactory)
                        .queryString("select d from PendingAuthDetailRecord d"
                                + " order by d.id.paAcctId asc, d.id.paAuthDate9c asc,"
                                + " d.id.paAuthTime9c asc")
                        .pageSize(CHUNK_SIZE)
                        .build();
        ItemProcessor<PendingAuthDetailRecord, String> processor =
                PendingAuthSegmentFormat::formatChild;
        return new StepBuilder(stepName, jobRepository)
                .<PendingAuthDetailRecord, String>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(lineWriter(stepName, outputPath))
                .build();
    }

    private ItemWriter<String> lineWriter(String stepName, String outputPath) {
        return new FlatFileItemWriterBuilder<String>()
                .name(stepName + "Writer")
                .resource(new FileSystemResource(outputPath))
                .lineAggregator(new PassThroughLineAggregator<>())
                .build();
    }
}
