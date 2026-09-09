package com.carddemo.batch.statement;

import com.carddemo.common.domain.TransactionRecord;
import com.carddemo.common.repository.TransactionRepository;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.mapping.PassThroughLineMapper;
import org.springframework.batch.item.file.transform.PassThroughLineAggregator;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Job CREASTMT - app/jcl/CREASTMT.JCL, the monthly account statement run of
 * S-13 StatementGeneration.
 *
 * <p>One step per {@code EXEC PGM=} (D-3):
 *
 * <table>
 *   <tr><td>DELDEF01</td><td>IDCAMS DELETE + DEFINE of the TRXFL work store</td></tr>
 *   <tr><td>STEP010</td><td>SORT FIELDS=(263,16,CH,A,1,16,CH,A) into the COSTM01 layout</td></tr>
 *   <tr><td>STEP020</td><td>IDCAMS REPRO of the sorted file into the keyed work store</td></tr>
 *   <tr><td>STEP030</td><td>IEFBR14 delete of the previous run's statement reports</td></tr>
 *   <tr><td>STEP040</td><td>CBSTM03A - write the text and HTML statements</td></tr>
 * </table>
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.profiles.active=postgres --spring.batch.job.name=CREASTMT
 * </pre>
 *
 * <p>The PDF rendering of app/jcl/TXT2PDF1.JCL is boundary B-13 and is not part of
 * this job; see the stream's migration plan.
 */
@Configuration
public class CreateStatementJobConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(CreateStatementJobConfiguration.class);

    private static final int CHUNK_SIZE = 100;

    /** AWS.M2.CARDDEMO.TRXFL.SEQ, SORTOUT of STEP010 (CREASTMT.JCL:47-50). */
    static final String WORK_FILE = "AWS.M2.CARDDEMO.TRXFL.SEQ";

    /** AWS.M2.CARDDEMO.STATEMNT.PS, the STMTFILE DD (CREASTMT.JCL:89). */
    static final String TEXT_FILE = "AWS.M2.CARDDEMO.STATEMNT.PS";

    /** AWS.M2.CARDDEMO.STATEMNT.HTML, the HTMLFILE DD (CREASTMT.JCL:93). */
    static final String HTML_FILE = "AWS.M2.CARDDEMO.STATEMNT.HTML";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final TransactionRepository transactions;
    private final StatementWorkTransactionRepository workTransactions;
    private final StatementFileAccess files;
    private final StatementAbend abend;
    private final StatementRenderer renderer;
    private final Path outputDir;

    public CreateStatementJobConfiguration(JobRepository jobRepository,
                                           PlatformTransactionManager transactionManager,
                                           EntityManagerFactory entityManagerFactory,
                                           TransactionRepository transactions,
                                           StatementWorkTransactionRepository workTransactions,
                                           StatementFileAccess files,
                                           StatementAbend abend,
                                           StatementRenderer renderer,
                                           @Value("${carddemo.batch.statement.output-dir:target/statements}")
                                           String outputDir) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.entityManagerFactory = entityManagerFactory;
        this.transactions = transactions;
        this.workTransactions = workTransactions;
        this.files = files;
        this.abend = abend;
        this.renderer = renderer;
        this.outputDir = Path.of(outputDir);
    }

    @Bean
    public Job createStatementJob() {
        return new JobBuilder("CREASTMT", jobRepository)
                .start(deleteDefineStep())
                .next(sortStep())
                .next(reproStep())
                .next(deleteReportsStep())
                .next(statementStep())
                .build();
    }

    /**
     * DELDEF01 - IDCAMS DELETE of TRXFL.SEQ and TRXFL.VSAM.KSDS followed by
     * DEFINE CLUSTER, with SET MAXCC = 0 so a first run does not fail
     * (CREASTMT.JCL:22-40).
     */
    private Step deleteDefineStep() {
        Tasklet tasklet = (contribution, chunkContext) -> {
            workTransactions.deleteAllInBatch();
            delete(WORK_FILE);
            Files.createDirectories(outputDir);
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder("DELDEF01", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    /**
     * STEP010 - SORT FIELDS=(263,16,CH,A,1,16,CH,A) with
     * OUTREC FIELDS=(1:263,16,17:1,262,279:279,50): TRANSACT in card-number then
     * transaction-id order, rewritten into the COSTM01 layout (CREASTMT.JCL:44-55).
     */
    private Step sortStep() {
        Map<String, Sort.Direction> sortFields = new LinkedHashMap<>();
        sortFields.put("cardNum", Sort.Direction.ASC);
        sortFields.put("id", Sort.Direction.ASC);

        ItemProcessor<TransactionRecord, String> processor =
                transaction -> TrnxLayout.fromTransact(TransactLayout.render(transaction));

        return new StepBuilder("STEP010", jobRepository)
                .<TransactionRecord, String>chunk(CHUNK_SIZE, transactionManager)
                .reader(new RepositoryItemReaderBuilder<TransactionRecord>()
                        .name("sortinReader")
                        .repository(transactions)
                        .methodName("findAll")
                        .sorts(sortFields)
                        .pageSize(CHUNK_SIZE)
                        .saveState(false)
                        .build())
                .processor(processor)
                .writer(new FlatFileItemWriterBuilder<String>()
                        .name("sortoutWriter")
                        .resource(new FileSystemResource(outputDir.resolve(WORK_FILE)))
                        .lineAggregator(new PassThroughLineAggregator<>())
                        .shouldDeleteIfExists(true)
                        .saveState(false)
                        .build())
                .build();
    }

    /**
     * STEP020 - IDCAMS REPRO INFILE(TRXFL.SEQ) OUTFILE(TRXFL.VSAM.KSDS)
     * (CREASTMT.JCL:56-62).
     */
    private Step reproStep() {
        ItemWriter<StatementWorkTransaction> writer = new JpaItemWriterBuilder<StatementWorkTransaction>()
                .entityManagerFactory(entityManagerFactory)
                .build();
        return new StepBuilder("STEP020", jobRepository)
                .<String, StatementWorkTransaction>chunk(CHUNK_SIZE, transactionManager)
                .reader(new FlatFileItemReaderBuilder<String>()
                        .name("reproReader")
                        .resource(new FileSystemResource(outputDir.resolve(WORK_FILE)))
                        .lineMapper(new PassThroughLineMapper())
                        .saveState(false)
                        .build())
                .processor(TrnxLayout::toWorkTransaction)
                .writer(writer)
                .build();
    }

    /**
     * STEP030 - IEFBR14 with DISP=(MOD,DELETE,DELETE) on STATEMNT.HTML and
     * STATEMNT.PS: the previous run's reports go away (CREASTMT.JCL:66-75).
     */
    private Step deleteReportsStep() {
        Tasklet tasklet = (contribution, chunkContext) -> {
            Files.createDirectories(outputDir);
            delete(TEXT_FILE);
            delete(HTML_FILE);
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder("STEP030", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    /** STEP040 - EXEC PGM=CBSTM03A (CREASTMT.JCL:79-96). */
    private Step statementStep() {
        FlatFileItemWriter<Statement> textWriter = new FlatFileItemWriterBuilder<Statement>()
                .name("stmtfileWriter")
                .resource(new FileSystemResource(outputDir.resolve(TEXT_FILE)))
                .lineAggregator(statement -> String.join("\n", renderer.text(statement)))
                .shouldDeleteIfExists(true)
                .saveState(false)
                .build();
        FlatFileItemWriter<Statement> htmlWriter = new FlatFileItemWriterBuilder<Statement>()
                .name("htmlfileWriter")
                .resource(new FileSystemResource(outputDir.resolve(HTML_FILE)))
                .lineAggregator(statement -> String.join("\n", renderer.html(statement)))
                .shouldDeleteIfExists(true)
                .saveState(false)
                .build();
        CompositeItemWriter<Statement> writer = new CompositeItemWriter<>(List.of(textWriter, htmlWriter));

        return new StepBuilder("STEP040", jobRepository)
                .<Statement, Statement>chunk(CHUNK_SIZE, transactionManager)
                .reader(new StatementItemReader(files, abend))
                .writer(writer)
                .build();
    }

    private void delete(String fileName) throws IOException {
        Path file = outputDir.resolve(fileName);
        if (Files.deleteIfExists(file)) {
            LOG.info("deleted {}", file);
        }
    }
}
