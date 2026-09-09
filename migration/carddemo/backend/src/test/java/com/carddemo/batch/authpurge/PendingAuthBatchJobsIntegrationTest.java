package com.carddemo.batch.authpurge;

import com.carddemo.common.domain.PendingAuthDetailId;
import com.carddemo.common.domain.PendingAuthDetailRecord;
import com.carddemo.common.domain.PendingAuthSummaryRecord;
import com.carddemo.common.repository.PendingAuthDetailRepository;
import com.carddemo.common.repository.PendingAuthSummaryRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The S-09 batch jobs end to end on their own in-memory database: UNLDPADB and
 * LOADPADB as a round trip (FR-U1..FR-U6), UNLDGSAM's two streams (FR-U7) and
 * CBPAUP0J's purge (FR-B6, FR-B7).
 */
@SpringBatchTest
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-pauthbatch;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PendingAuthBatchJobsIntegrationTest {

    @TempDir
    static Path dir;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    @Qualifier("pendingAuthUnloadJob")
    private Job unloadJob;
    @Autowired
    @Qualifier("pendingAuthGsamUnloadJob")
    private Job gsamUnloadJob;
    @Autowired
    @Qualifier("pendingAuthLoadJob")
    private Job loadJob;
    @Autowired
    @Qualifier("pendingAuthPurgeJob")
    private Job purgeJob;
    @Autowired
    private PendingAuthSummaryRepository summaries;
    @Autowired
    private PendingAuthDetailRepository details;


    @Test
    @Order(1)
    void frU5_unldpadbWritesTheTwoFilesLoadpadbReadsBack() throws Exception {
        long rootCount = summaries.count();
        long childCount = details.count();
        assertThat(rootCount).isPositive();
        assertThat(childCount).isPositive();

        Path roots = dir.resolve("pautdb.root.txt");
        Path children = dir.resolve("pautdb.child.txt");
        jobLauncherTestUtils.setJob(unloadJob);
        JobExecution unload = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", "unload-1")
                .addString("outfil1", roots.toString())
                .addString("outfil2", children.toString())
                .toJobParameters());

        assertThat(unload.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(Files.readAllLines(roots)).hasSize((int) rootCount)
                .allMatch(line -> line.length() == PendingAuthSegmentFormat.ROOT_LENGTH);
        assertThat(Files.readAllLines(children)).hasSize((int) childCount)
                .allMatch(line -> line.length() == PendingAuthSegmentFormat.CHILD_LENGTH);

        details.deleteAllInBatch();
        summaries.deleteAllInBatch();

        jobLauncherTestUtils.setJob(loadJob);
        JobExecution load = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", "load-1")
                .addString("infile1", roots.toString())
                .addString("infile2", children.toString())
                .toJobParameters());

        assertThat(load.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(summaries.count()).isEqualTo(rootCount);
        assertThat(details.count()).isEqualTo(childCount);
    }

    @Test
    @Order(2)
    void frU6_reloadingTheSameFilesSkipsTheSegmentsAlreadyInTheDatabase() throws Exception {
        // PAUDBLOD treats an II status as informational and carries on.
        long rootCount = summaries.count();
        long childCount = details.count();

        jobLauncherTestUtils.setJob(loadJob);
        JobExecution load = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", "load-2")
                .addString("infile1", dir.resolve("pautdb.root.txt").toString())
                .addString("infile2", dir.resolve("pautdb.child.txt").toString())
                .toJobParameters());

        assertThat(load.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(summaries.count()).isEqualTo(rootCount);
        assertThat(details.count()).isEqualTo(childCount);
    }

    @Test
    @Order(3)
    void frU7_unldgsamWritesTheSameRecordsThroughItsTwoGsamStreams() throws Exception {
        Path roots = dir.resolve("pautdb.root.gsam.txt");
        Path children = dir.resolve("pautdb.child.gsam.txt");

        jobLauncherTestUtils.setJob(gsamUnloadJob);
        JobExecution execution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", "gsam-1")
                .addString("pasfilop", roots.toString())
                .addString("padfilop", children.toString())
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(Files.readAllLines(roots))
                .isEqualTo(Files.readAllLines(dir.resolve("pautdb.root.txt")));
        assertThat(Files.readAllLines(children))
                .isEqualTo(Files.readAllLines(dir.resolve("pautdb.child.txt")));
    }

    @Test
    @Order(4)
    void frB6_cbpaup0jDeletesExpiredChildrenAndTheirRootAndKeepsTheRest() throws Exception {
        LocalDate today = LocalDate.now();
        int todayYyddd = (today.getYear() % 100) * 1000 + today.getDayOfYear();
        LocalDate old = today.minusDays(30);
        int oldYyddd = (old.getYear() % 100) * 1000 + old.getDayOfYear();

        summaries.save(root(9999L, 2, 0));
        details.save(child(9999L, 99999 - oldYyddd, "00", "10.00"));
        details.save(child(9999L, 99999 - todayYyddd, "00", "20.00"));

        jobLauncherTestUtils.setJob(purgeJob);
        JobExecution execution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", "purge-1")
                .addString("expiryDays", "5")
                .addString("chkpFreq", "5")
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        // One approval expired, one is a day old: the count reaches 1, so the
        // root survives and only the expired child is gone.
        assertThat(summaries.findById(9999L)).isPresent();
        List<PendingAuthDetailRecord> left = details.findAll().stream()
                .filter(d -> d.getId().getPaAcctId().equals(9999L))
                .toList();
        assertThat(left).hasSize(1);
        assertThat(left.get(0).getId().getPaAuthDate9c()).isEqualTo(99999 - todayYyddd);
    }

    @Test
    @Order(5)
    void frB7_aRootWhoseApprovalsHaveAllExpiredGoesWithItsChildren() throws Exception {
        LocalDate old = LocalDate.now().minusDays(30);
        int oldYyddd = (old.getYear() % 100) * 1000 + old.getDayOfYear();

        summaries.save(root(9998L, 1, 0));
        details.save(child(9998L, 99999 - oldYyddd, "00", "10.00"));

        jobLauncherTestUtils.setJob(purgeJob);
        JobExecution execution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", "purge-2")
                .addString("expiryDays", "5")
                .addString("chkpFreq", "5")
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(summaries.findById(9998L)).isEmpty();
        assertThat(details.findAll().stream()
                .filter(d -> d.getId().getPaAcctId().equals(9998L))).isEmpty();
    }

    private static PendingAuthSummaryRecord root(long acctId, int approvedCnt, int declinedCnt) {
        PendingAuthSummaryRecord s = new PendingAuthSummaryRecord();
        s.setPaAcctId(acctId);
        s.setPaCustId(acctId);
        s.setPaAuthStatus("A");
        s.setPaAccountStatus1("AC");
        s.setPaAccountStatus2("  ");
        s.setPaAccountStatus3("  ");
        s.setPaAccountStatus4("  ");
        s.setPaAccountStatus5("  ");
        s.setPaCreditLimit(new BigDecimal("2020.00"));
        s.setPaCashLimit(new BigDecimal("1020.00"));
        s.setPaCreditBalance(new BigDecimal("30.00"));
        s.setPaCashBalance(BigDecimal.ZERO);
        s.setPaApprovedAuthCnt(approvedCnt);
        s.setPaDeclinedAuthCnt(declinedCnt);
        s.setPaApprovedAuthAmt(new BigDecimal("30.00"));
        s.setPaDeclinedAuthAmt(BigDecimal.ZERO);
        return s;
    }

    private static PendingAuthDetailRecord child(long acctId, int date9c, String respCode,
                                                 String amount) {
        PendingAuthDetailRecord d = new PendingAuthDetailRecord();
        d.setId(new PendingAuthDetailId(acctId, date9c, 849999999L));
        d.setPaAuthOrigDate("250115");
        d.setPaAuthOrigTime("150000");
        d.setPaCardNum("9680294154603697");
        d.setPaAuthType("0100");
        d.setPaCardExpiryDate("2605");
        d.setPaMessageType("0100");
        d.setPaMessageSource("POS");
        d.setPaAuthIdCode("150000");
        d.setPaAuthRespCode(respCode);
        d.setPaAuthRespReason("00".equals(respCode) ? "0000" : "4100");
        d.setPaProcessingCode(1000L);
        d.setPaTransactionAmt(new BigDecimal(amount));
        d.setPaApprovedAmt("00".equals(respCode) ? new BigDecimal(amount) : BigDecimal.ZERO);
        d.setPaMerchantCatagoryCode("5411");
        d.setPaAcqrCountryCode("840");
        d.setPaPosEntryMode(5);
        d.setPaMerchantId("900000001");
        d.setPaMerchantName("ACME SUPERMARKET");
        d.setPaMerchantCity("NEW YORK");
        d.setPaMerchantState("NY");
        d.setPaMerchantZip("10001");
        d.setPaTransactionId("PAUTHB000000001");
        d.setPaMatchStatus("00".equals(respCode) ? "P" : "D");
        d.setPaAuthFraud(" ");
        d.setPaFraudRptDate(" ");
        return d;
    }
}
