package com.carddemo.transaction.repository;

import com.carddemo.transaction.entity.CardXrefRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip and account-index coverage for {@link CardXrefRepository}, backing
 * CT02's account<->card resolution. Runs against the seeded H2 DB.
 * {@code @Transactional} rolls back save() writes to keep the shared DB pristine.
 */
@SpringBootTest
@Transactional
class CardXrefRepositoryTest {

    @Autowired
    private CardXrefRepository repository;

    @Test
    void savesAndFindsByCardNum() {
        CardXrefRecord x = new CardXrefRecord();
        x.setCardNum("4999999999999999");
        x.setCustId(111000099L);
        x.setAcctId(10000000099L);
        repository.save(x);

        Optional<CardXrefRecord> found = repository.findById("4999999999999999");

        assertThat(found).isPresent();
        assertThat(found.get().getAcctId()).isEqualTo(10000000099L);
        assertThat(found.get().getCustId()).isEqualTo(111000099L);
    }

    @Test
    void findByAcctIdResolvesCardFromAccount() {
        Optional<CardXrefRecord> found = repository.findByAcctId(10000000001L);

        assertThat(found).isPresent();
        assertThat(found.get().getCardNum()).isEqualTo("4111111111111111");
    }

    @Test
    void findByAcctIdReturnsEmptyWhenAbsent() {
        assertThat(repository.findByAcctId(88888888888L)).isEmpty();
    }

    @Test
    void seedXrefLoads() {
        assertThat(repository.count()).isGreaterThanOrEqualTo(5);
    }
}
