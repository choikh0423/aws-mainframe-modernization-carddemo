package com.carddemo.statements;

import com.carddemo.common.batch.AbendException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CREASTMT on the seeded estate archives one statement per card per cycle.
 * Runs without a servlet environment, as the batch command line does.
 */
@SpringBatchTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-statement-archive;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "carddemo.batch.statement.output-dir=target/statements-archive-test"
})
class StatementArchiveJobTest {

    private static final Path OUTPUT_DIR = Path.of("target/statements-archive-test");
    private static final String TEXT_FILE = "AWS.M2.CARDDEMO.STATEMNT.PS";
    private static final String HTML_FILE = "AWS.M2.CARDDEMO.STATEMNT.HTML";

    /** card_xref row of the ASCII estate: customer 50, account 50. */
    private static final String CARD = "0500024453765740";

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    @Qualifier("createStatementJob")
    private Job createStatementJob;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StatementArchiveRepository archive;

    private int seededCards;

    @BeforeEach
    void resetEstate() {
        jobLauncherTestUtils.setJob(createStatementJob);
        // The transaction demo seed's five card_xref rows have no customer or
        // account, which CBSTM03A faithfully abends on (CreateStatementJobTest).
        jdbcTemplate.update("DELETE FROM card_xref WHERE cust_id > 50");
        jdbcTemplate.update("DELETE FROM transactions WHERE card_num = '" + CARD + "'");
        jdbcTemplate.update("INSERT INTO transactions (id, type_cd, cat_cd, source, description,"
                + " amount, merchant_id, merchant_name, merchant_city, merchant_zip, card_num,"
                + " orig_ts, proc_ts) VALUES"
                + " ('0000000000090042', '01', 1000, 'POS', 'GROCERY PURCHASE', 13.75, 900000001,"
                + " 'ACME SUPERMARKET', 'NEW YORK', '10001', '" + CARD + "',"
                + " '2026-10-01-10.15.31.000000', '2026-10-01-23.59.51.000000'),"
                + " ('0000000000090043', '02', 2000, 'ONLINE', 'FUEL PURCHASE', -17.50, 900000002,"
                + " 'GLOBEX FUEL', 'CHICAGO', '60601', '" + CARD + "',"
                + " '2026-10-02-10.15.32.000000', '2026-10-02-23.59.52.000000')");
        archive.deleteAllInBatch();
        seededCards = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM card_xref", Integer.class);
    }

    @Test
    void archivesOneRowPerSeededCardHoldingExactlyItsSliceOfTheReports() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(parameters("2026-10"));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions()).extracting(StepExecution::getStepName)
                .containsExactly("DELDEF01", "STEP010", "STEP020", "STEP030", "STEP040");
        assertThat(seededCards).isEqualTo(50);
        assertThat(archive.count()).isEqualTo(seededCards);
        assertThat(archive.countByCycle("2026-10")).isEqualTo(seededCards);

        Map<String, String> textSlices = slices(read(TEXT_FILE),
                line -> line.contains("START OF STATEMENT"), line -> line.contains("END OF STATEMENT"),
                "Account ID         :");
        Map<String, String> htmlSlices = slices(read(HTML_FILE),
                line -> line.startsWith("<!DOCTYPE html>"), line -> line.startsWith("</html>"),
                "<h3>Statement for Account Number: ");
        assertThat(textSlices).hasSize(seededCards);
        assertThat(htmlSlices).hasSize(seededCards);

        for (StatementArchiveRecord row : archive.findAll()) {
            String acct = String.format("%011d", row.getAcctId());
            assertThat(row.getCycle()).isEqualTo("2026-10");
            assertThat(row.getTextBody()).as("text_body of %s", row.getCardNum())
                    .isEqualTo(textSlices.get(acct));
            assertThat(row.getHtmlBody()).as("html_body of %s", row.getCardNum())
                    .isEqualTo(htmlSlices.get(acct));
            Integer xrefAcct = jdbcTemplate.queryForObject(
                    "SELECT acct_id FROM card_xref WHERE card_num = ?", Integer.class, row.getCardNum());
            assertThat(row.getAcctId()).isEqualTo(xrefAcct.longValue());
        }

        StatementArchiveRecord card = archive.findByCardNumAndCycle(CARD, "2026-10").orElseThrow();
        assertThat(card.getAcctId()).isEqualTo(50L);
        assertThat(card.getTxnCount()).isEqualTo(2);
        assertThat(card.getTotalAmount()).isEqualByComparingTo("-3.75");
        assertThat(card.getTextBody()).contains("Total EXP:" + " ".repeat(56) + "$        3.75-");
    }

    @Test
    void rerunningACycleReplacesItsRowsAndLeavesOtherCyclesAlone() throws Exception {
        jobLauncherTestUtils.launchJob(parameters("2026-10"));
        List<Long> firstRunIds = archive.findAll().stream().map(StatementArchiveRecord::getStmtId).sorted().toList();

        StatementArchiveRecord september = row("9999000011112222", "2026-09");
        archive.saveAndFlush(september);
        archive.saveAndFlush(row("9999000033334444", "2026-10"));

        JobExecution rerun = jobLauncherTestUtils.launchJob(parameters("2026-10"));

        assertThat(rerun.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(archive.countByCycle("2026-10")).isEqualTo(seededCards);
        assertThat(archive.findByCardNumAndCycle("9999000033334444", "2026-10")).isEmpty();
        assertThat(archive.findAll().stream()
                .filter(r -> r.getCycle().equals("2026-10"))
                .map(StatementArchiveRecord::getStmtId).sorted().toList())
                .isEqualTo(firstRunIds);

        StatementArchiveRecord untouched = archive.findByCardNumAndCycle("9999000011112222", "2026-09").orElseThrow();
        assertThat(untouched.getStmtId()).isEqualTo(september.getStmtId());
        assertThat(untouched.getTextBody()).isEqualTo("old");
        assertThat(untouched.getGeneratedTs()).isEqualTo(LocalDateTime.of(2026, 10, 1, 2, 0));
        assertThat(archive.count()).isEqualTo(seededCards + 1L);
    }

    @Test
    void anOmittedCycleIsTheCurrentMonth() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("run", UUID.randomUUID().toString())
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(archive.countByCycle(YearMonth.now().toString())).isEqualTo(seededCards);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "2026-00", "2026-1", "202610", "2026/10", "26-10", "", "2026-10-01"})
    void aBadCycleAbendsTheJobBeforeAnyStep(String cycle) throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(parameters(cycle));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getStepExecutions()).isEmpty();
        assertThat(archive.count()).isZero();
        assertThat(execution.getAllFailureExceptions())
                .anySatisfy(failure -> assertThat(failure)
                        .isInstanceOf(AbendException.class)
                        .hasMessageContaining("ABEND-CULPRIT=CREASTMT")
                        .hasMessageContaining("INVALID CYCLE PARAMETER '" + cycle + "' - EXPECTED YYYY-MM"));
    }

    private static StatementArchiveRecord row(String card, String cycle) {
        StatementArchiveRecord r = new StatementArchiveRecord();
        r.setAcctId(99_999_999_001L);
        r.setCardNum(card);
        r.setCycle(cycle);
        r.setGeneratedTs(LocalDateTime.of(2026, 10, 1, 2, 0));
        r.setTxnCount(0);
        r.setTotalAmount(BigDecimal.ZERO);
        r.setTextBody("old");
        r.setHtmlBody("<p>old</p>");
        return r;
    }

    /** Each statement's lines from its first to its last record, keyed by the 11-digit account id. */
    private static Map<String, String> slices(List<String> lines, Predicate<String> first,
                                              Predicate<String> last, String accountLabel) {
        Map<String, String> slices = new HashMap<>();
        List<String> current = null;
        String account = null;
        for (String line : lines) {
            if (first.test(line)) {
                current = new ArrayList<>();
                account = null;
            }
            current.add(line);
            if (line.startsWith(accountLabel)) {
                account = line.substring(accountLabel.length(), accountLabel.length() + 11);
            }
            if (last.test(line)) {
                slices.put(account, String.join("\n", current));
                current = null;
            }
        }
        return slices;
    }

    private static JobParameters parameters(String cycle) {
        return new JobParametersBuilder()
                .addString("run", UUID.randomUUID().toString())
                .addString(StatementCycle.PARAMETER, cycle)
                .toJobParameters();
    }

    private static List<String> read(String fileName) throws IOException {
        return Files.readAllLines(OUTPUT_DIR.resolve(fileName));
    }
}
