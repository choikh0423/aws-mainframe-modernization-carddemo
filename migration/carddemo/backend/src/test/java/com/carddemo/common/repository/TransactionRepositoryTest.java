package com.carddemo.common.repository;

import com.carddemo.common.domain.TransactionRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip and browse-helper coverage for {@link TransactionRepository}.
 * Runs against the H2 in-memory DB seeded by schema.sql + data.sql (30 rows).
 * {@code @Transactional} rolls back the save() writes so the JVM-shared in-memory
 * DB stays at the 30-row baseline for order-independent runs of the rest of the suite.
 */
@SpringBootTest
@Transactional
class TransactionRepositoryTest {

    @Autowired
    private TransactionRepository repository;

    private static TransactionRecord tx(String id, BigDecimal amount) {
        TransactionRecord t = new TransactionRecord();
        t.setId(id);
        t.setTypeCd("01");
        t.setCatCd(1000);
        t.setSource("POS");
        t.setDescription("TEST TRANSACTION");
        t.setAmount(amount);
        t.setMerchantId(900000001L);
        t.setMerchantName("TEST MERCHANT");
        t.setMerchantCity("TEST CITY");
        t.setMerchantZip("00000");
        t.setCardNum("4111111111111111");
        t.setOrigTs("2023-06-01-10.15.31.000000");
        t.setProcTs("2023-06-01-23.59.51.000000");
        return t;
    }

    @Test
    void savesAndFindsById() {
        TransactionRecord saved = repository.save(tx("0000000000000500", new BigDecimal("123.45")));

        Optional<TransactionRecord> found = repository.findById("0000000000000500");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo("0000000000000500");
        assertThat(found.get().getAmount()).isEqualByComparingTo("123.45");
        assertThat(found.get().getCardNum()).isEqualTo("4111111111111111");
        assertThat(saved.getCatCd()).isEqualTo(1000);
    }

    @Test
    void seedDataLoads() {
        assertThat(repository.findById("0000000000000001")).isPresent();
        assertThat(repository.count()).isGreaterThanOrEqualTo(30);
    }

    @Test
    void findTopByOrderByIdDescReturnsMaxKey() {
        repository.save(tx("0000000000009999", new BigDecimal("1.00")));

        Optional<TransactionRecord> top = repository.findTopByOrderByIdDesc();

        assertThat(top).isPresent();
        assertThat(top.get().getId()).isEqualTo("0000000000009999");
    }

    @Test
    void findAllByOrderByIdAscPagesTenPerScreen() {
        List<TransactionRecord> firstPage = repository.findAllByOrderByIdAsc(PageRequest.of(0, 10));

        assertThat(firstPage).hasSize(10);
        assertThat(firstPage.get(0).getId()).isEqualTo("0000000000000001");
        assertThat(firstPage).isSortedAccordingTo((a, b) -> a.getId().compareTo(b.getId()));
    }

    @Test
    void findByIdGreaterThanPagesForward() {
        List<TransactionRecord> next = repository.findByIdGreaterThanOrderByIdAsc(
                "0000000000000010", PageRequest.of(0, 10));

        assertThat(next).hasSize(10);
        assertThat(next.get(0).getId()).isEqualTo("0000000000000011");
    }

    @Test
    void findByIdLessThanPagesBackward() {
        List<TransactionRecord> prev = repository.findByIdLessThanOrderByIdDesc(
                "0000000000000011", PageRequest.of(0, 10));

        assertThat(prev).hasSize(10);
        assertThat(prev.get(0).getId()).isEqualTo("0000000000000010");
    }
}
