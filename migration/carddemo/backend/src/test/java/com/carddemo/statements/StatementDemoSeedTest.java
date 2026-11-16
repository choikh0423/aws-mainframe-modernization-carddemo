package com.carddemo.statements;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * CMD-9: with {@code carddemo.statements.seed-on-startup=true} the app boots with the
 * previous and current cycle archived for every seeded card, on the stock seed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-statement-seed;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "carddemo.batch.statement.output-dir=target/statements-seed-test",
        "carddemo.statements.seed-on-startup=true"
})
class StatementDemoSeedTest {

    /** Cards of the ASCII estate (customer and account 1-50). */
    private static final int ESTATE_CARDS = 50;

    /** The CT02 fixture cards of R__seed_transaction_demo_data.sql, with no customer or account. */
    private static final int CT02_FIXTURE_CARDS = 5;

    @Autowired
    private StatementArchiveRepository archive;
    @Autowired
    private StatementDemoSeed seed;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void bootArchivesThePreviousAndCurrentCycleForEverySeededCard() {
        YearMonth current = YearMonth.now();
        String previousCycle = current.minusMonths(1).toString();
        String currentCycle = current.toString();

        assertThat(archive.countByCycle(previousCycle)).isEqualTo(ESTATE_CARDS);
        assertThat(archive.countByCycle(currentCycle)).isEqualTo(ESTATE_CARDS);
        assertThat(archive.count()).isEqualTo(2L * ESTATE_CARDS);

        assertThat(archive.findByAcctIdOrderByCycleDesc(1L, Pageable.unpaged()).getContent())
                .extracting(StatementArchiveRecord::getCycle)
                .containsExactly(currentCycle, previousCycle);
    }

    @Test
    void putsTheCt02FixtureXrefsBackAfterItsRuns() {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM card_xref", Integer.class))
                .isEqualTo(ESTATE_CARDS + CT02_FIXTURE_CARDS);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT acct_id FROM card_xref WHERE card_num = '4111111111111111'", Long.class))
                .isEqualTo(10000000001L);
    }

    @Test
    void reseedingReplacesBothCyclesInsteadOfDuplicatingThem() {
        seed.seed();

        assertThat(archive.count()).isEqualTo(2L * ESTATE_CARDS);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM card_xref", Integer.class))
                .isEqualTo(ESTATE_CARDS + CT02_FIXTURE_CARDS);
    }

    @Test
    void seedsTheMonthBeforeAndTheMonthOfTheClock() {
        Clock january = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC);
        StatementDemoSeed seeded = new StatementDemoSeed(
                mock(JobLauncher.class), mock(Job.class), jdbcTemplate, "", january);

        assertThat(seeded.cycles()).containsExactly("2025-12", "2026-01");
    }

    @Test
    void doesNothingWhenTheProcessIsABatchRun() {
        JobLauncher launcher = mock(JobLauncher.class);
        StatementDemoSeed batchRun = new StatementDemoSeed(
                launcher, mock(Job.class), mock(JdbcTemplate.class), "CREASTMT", Clock.systemUTC());

        batchRun.start();

        verifyNoInteractions(launcher);
    }
}
