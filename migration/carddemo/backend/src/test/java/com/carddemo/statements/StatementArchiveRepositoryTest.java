package com.carddemo.statements;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs on the profile's own datasource (H2 by default, PostgreSQL with
 * {@code -Dspring.profiles.active=postgres}) so the V2100 Flyway script, not a
 * generated schema, is what is exercised.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StatementArchiveRepositoryTest {

    private static final long ACCT = 99_999_999_001L;
    private static final String CARD = "9999000011112222";

    @Autowired
    private StatementArchiveRepository repository;

    @Test
    void insertAssignsIdAndRoundTripsEveryColumn() {
        String text = "*".repeat(31) + "START OF STATEMENT" + "*".repeat(31) + "\n".repeat(400);
        String html = "<html><body>" + "<tr><td>x</td></tr>".repeat(2_000) + "</body></html>";
        StatementArchiveRecord saved = repository.saveAndFlush(
                statement(CARD, "2026-10", 3, new BigDecimal("-1234.56"), text, html));

        assertThat(saved.getStmtId()).isNotNull();

        Optional<StatementArchiveRecord> found = repository.findByCardNumAndCycle(CARD, "2026-10");
        assertThat(found).isPresent();
        StatementArchiveRecord row = found.get();
        assertThat(row.getStmtId()).isEqualTo(saved.getStmtId());
        assertThat(row.getAcctId()).isEqualTo(ACCT);
        assertThat(row.getCycle()).isEqualTo("2026-10");
        assertThat(row.getGeneratedTs()).isEqualTo(LocalDateTime.of(2026, 11, 1, 2, 30, 0));
        assertThat(row.getTxnCount()).isEqualTo(3);
        assertThat(row.getTotalAmount()).isEqualByComparingTo("-1234.56");
        assertThat(row.getTextBody()).isEqualTo(text);
        assertThat(row.getHtmlBody()).isEqualTo(html);

        assertThat(repository.findByCardNumAndCycle(CARD, "2026-09")).isEmpty();
    }

    @Test
    void duplicateCardAndCycleViolatesUniqueKey() {
        repository.saveAndFlush(statement(CARD, "2026-10", 1, BigDecimal.TEN, "a", "<p>a</p>"));

        assertThatThrownBy(() -> repository.saveAndFlush(
                statement(CARD, "2026-10", 2, BigDecimal.ONE, "b", "<p>b</p>")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameCycleOnAnotherCardIsAllowed() {
        repository.saveAndFlush(statement(CARD, "2026-10", 1, BigDecimal.TEN, "a", "<p>a</p>"));
        repository.saveAndFlush(statement("9999000033334444", "2026-10", 1, BigDecimal.TEN, "b", "<p>b</p>"));

        assertThat(repository.findByAcctIdOrderByCycleDesc(ACCT, PageRequest.of(0, 10)).getTotalElements())
                .isEqualTo(2);
    }

    @Test
    void listsAnAccountsStatementsNewestCycleFirstAndPages() {
        repository.saveAndFlush(statement(CARD, "2026-08", 1, BigDecimal.ONE, "a", "<p>a</p>"));
        repository.saveAndFlush(statement(CARD, "2026-10", 1, BigDecimal.ONE, "c", "<p>c</p>"));
        repository.saveAndFlush(statement(CARD, "2025-12", 1, BigDecimal.ONE, "z", "<p>z</p>"));
        repository.saveAndFlush(statement(CARD, "2026-09", 1, BigDecimal.ONE, "b", "<p>b</p>"));
        StatementArchiveRecord other = statement("9999000055556666", "2026-11", 1, BigDecimal.ONE, "o", "<p>o</p>");
        other.setAcctId(ACCT + 1);
        repository.saveAndFlush(other);

        Page<StatementArchiveRecord> first = repository.findByAcctIdOrderByCycleDesc(ACCT, PageRequest.of(0, 3));
        assertThat(first.getContent()).extracting(StatementArchiveRecord::getCycle)
                .containsExactly("2026-10", "2026-09", "2026-08");
        assertThat(first.getTotalElements()).isEqualTo(4);

        Page<StatementArchiveRecord> second = repository.findByAcctIdOrderByCycleDesc(ACCT, PageRequest.of(1, 3));
        assertThat(second.getContent()).extracting(StatementArchiveRecord::getCycle)
                .containsExactly("2025-12");
    }

    private static StatementArchiveRecord statement(String card, String cycle, int txnCount,
                                                    BigDecimal total, String text, String html) {
        StatementArchiveRecord r = new StatementArchiveRecord();
        r.setAcctId(ACCT);
        r.setCardNum(card);
        r.setCycle(cycle);
        r.setGeneratedTs(LocalDateTime.of(2026, 11, 1, 2, 30, 0));
        r.setTxnCount(txnCount);
        r.setTotalAmount(total);
        r.setTextBody(text);
        r.setHtmlBody(html);
        return r;
    }
}
