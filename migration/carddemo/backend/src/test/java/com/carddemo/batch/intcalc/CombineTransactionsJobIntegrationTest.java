package com.carddemo.batch.intcalc;

import com.carddemo.common.domain.TransactionRecord;
import com.carddemo.common.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * COMBTRAN end to end, including the INTCALC -> COMBTRAN hand-over the Control-M
 * chain performs (CLOSEFIL -> INTCALC -> COMBTRAN -> WAITSTEP -> OPENFIL).
 */
@SpringBatchTest
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-combtran;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class CombineTransactionsJobIntegrationTest {

    @TempDir
    Path workDir;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    @Qualifier("combineTransactionsJob")
    private Job combineTransactionsJob;
    @Autowired
    @Qualifier("interestCalculationJob")
    private Job interestCalculationJob;
    @Autowired
    private TransactionRepository transactions;

    @BeforeEach
    void useCombineTransactionsJob() {
        jobLauncherTestUtils.setJob(combineTransactionsJob);
    }

    /** FR-C1, FR-C2, FR-C3: two steps, the concatenated input, sorted on TRAN-ID. */
    @Test
    void runsBothStepsAndSortsTheCombinedFileByTransactionId() throws Exception {
        Path systran = writeSystran("combtran-sort",
                interestTransaction("9999999999000002", "10.00"),
                interestTransaction("0000000000000001", "20.00"));
        Path combined = workDir.resolve("combtran-sort.combined.txt");

        JobExecution execution = jobLauncherTestUtils.launchJob(parameters("combtran-sort", systran, combined));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions()).extracting(StepExecution::getStepName)
                .containsExactly("STEP05R", "STEP10");

        List<String> lines = Files.readAllLines(combined);
        List<String> ids = lines.stream().map(TransactionRecordLine::tranIdOf).toList();
        assertThat(ids).isSorted();
        assertThat(ids).contains("0000000000000001", "9999999999000002");
        assertThat(lines).allSatisfy(line -> assertThat(line).hasSize(350));
    }

    /** FR-C2, FR-C4: the master's own records plus SYSTRAN, all loaded back. */
    @Test
    void combinesTheMasterWithSystranAndReprosItBack() throws Exception {
        long masterCountBefore = transactions.count();
        Path systran = writeSystran("combtran-repro",
                interestTransaction("2022071800900001", "12.49"));

        JobExecution execution = jobLauncherTestUtils.launchJob(parameters("combtran-repro", systran,
                workDir.resolve("combtran-repro.combined.txt")));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(transactions.count()).isEqualTo(masterCountBefore + 1);
        TransactionRecord loaded = transactions.findById("2022071800900001").orElseThrow();
        assertThat(loaded.getAmount()).isEqualByComparingTo("12.49");
        assertThat(loaded.getTypeCd()).isEqualTo("01");
        assertThat(loaded.getCatCd()).isEqualTo(5);
        assertThat(loaded.getSource()).isEqualTo("System");
        assertThat(loaded.getCardNum()).isEqualTo("4111111111111111");
        // FR-C4: reproducing records that were already on the master is a no-op
        assertThat(transactions.count()).isEqualTo(masterCountBefore + 1);
    }

    /** FR-C6: no SYSTRAN generation means a failed SORTIN allocation, not a partial load. */
    @Test
    void failsWhenTheSystranGenerationIsMissing() throws Exception {
        long masterCountBefore = transactions.count();

        JobExecution execution = jobLauncherTestUtils.launchJob(parameters("combtran-missing",
                workDir.resolve("absent.systran.txt"),
                workDir.resolve("combtran-missing.combined.txt")));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(transactions.count()).isEqualTo(masterCountBefore);
    }

    /** The Control-M hand-over: what INTCALC writes is what COMBTRAN loads. */
    @Test
    void loadsTheInterestTransactionsInterestCalculationJustGenerated() throws Exception {
        Path systran = workDir.resolve("chain.systran.txt");
        jobLauncherTestUtils.setJob(interestCalculationJob);
        JobExecution intcalc = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run.date", "2022071800")
                .addString("systran.file", systran.toString())
                .addString("run", "chain")
                .toJobParameters());
        assertThat(intcalc.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        long masterCountBefore = transactions.count();
        jobLauncherTestUtils.setJob(combineTransactionsJob);
        JobExecution combtran = jobLauncherTestUtils.launchJob(
                parameters("chain", systran, workDir.resolve("chain.combined.txt")));

        assertThat(combtran.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(transactions.count()).isEqualTo(masterCountBefore + 50);
        assertThat(transactions.findById("2022071800000001").orElseThrow().getDescription())
                .isEqualTo("Int. for a/c 00000000001");
    }

    private org.springframework.batch.core.JobParameters parameters(String run, Path systran, Path combined) {
        return new JobParametersBuilder()
                .addString("run.date", "2022071800")
                .addString("systran.file", systran.toString())
                .addString("combined.file", combined.toString())
                .addString("run", run)
                .toJobParameters();
    }

    private Path writeSystran(String run, TransactionRecord... generated) throws Exception {
        Path systran = workDir.resolve(run + ".systran.txt");
        Files.write(systran,
                java.util.Arrays.stream(generated).map(TransactionRecordLine::format).toList(),
                StandardCharsets.UTF_8);
        return systran;
    }

    private static TransactionRecord interestTransaction(String id, String amount) {
        TransactionRecord tran = new TransactionRecord();
        tran.setId(id);
        tran.setTypeCd("01");
        tran.setCatCd(5);
        tran.setSource("System");
        tran.setDescription("Int. for a/c 00000000001");
        tran.setAmount(new BigDecimal(amount));
        tran.setMerchantId(0L);
        tran.setMerchantName("");
        tran.setMerchantCity("");
        tran.setMerchantZip("");
        tran.setCardNum("4111111111111111");
        tran.setOrigTs("2022-07-18-10.04.05.670000");
        tran.setProcTs("2022-07-18-10.04.05.670000");
        return tran;
    }
}
