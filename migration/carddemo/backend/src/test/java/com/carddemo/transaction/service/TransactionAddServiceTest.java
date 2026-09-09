package com.carddemo.transaction.service;

import com.carddemo.transaction.dto.TransactionAddRequest;
import com.carddemo.transaction.dto.TransactionAddResponse;
import com.carddemo.common.domain.TransactionRecord;
import com.carddemo.transaction.exception.CardXrefNotFoundException;
import com.carddemo.transaction.exception.TransactionValidationException;
import com.carddemo.common.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-A1/FR-A2 (resolve) + FR-A6 (write, key-gen, persisted row) coverage for
 * {@link TransactionAddService} against the seeded H2 DB (30 transactions ->
 * next Tran ID 31; card_xref rows for resolution). Traces to COTRN02C's
 * PROCESS-ENTER-KEY / ADD-TRANSACTION (COTRN02C.cbl:164-466, 711-749).
 *
 * <p>{@code @Transactional} rolls back each write so the shared in-memory DB
 * (DB_CLOSE_DELAY=-1, reused across the cached context) stays at 30 rows and the
 * CT00 list tests keep passing regardless of execution order.
 */
@SpringBootTest
@Transactional
class TransactionAddServiceTest {

    @Autowired
    private TransactionAddService service;

    @Autowired
    private TransactionRepository repository;

    private static TransactionAddRequest validRequest() {
        TransactionAddRequest r = new TransactionAddRequest();
        r.setAccountId("10000000001");
        r.setCardNumber("");
        r.setTypeCd("07");
        r.setCategoryCd("1234");
        r.setSource("POS");
        r.setDescription("NEW TRANSACTION");
        r.setAmount("-00000123.45");
        r.setOrigDate("2023-07-01");
        r.setProcDate("2023-07-02");
        r.setMerchantId("900000009");
        r.setMerchantName("NEW MERCHANT");
        r.setMerchantCity("DENVER");
        r.setMerchantZip("80202");
        r.setConfirm("Y");
        return r;
    }

    @Test
    void frA6_confirmed_writesRowWithMaxKeyPlusOneAndGreenMessage() {
        TransactionAddResponse response = service.add(validRequest());

        // Key generation: highest existing (30) + 1, zero-padded to 16 (cbl:444-451).
        assertThat(response.getTranId()).isEqualTo("0000000000000031");
        assertThat(response.getMessage())
                .isEqualTo("Transaction added successfully.  Your Tran ID is 0000000000000031.");

        // Persisted-row assertion: the record is actually written (FR-A6, cbl:711-734).
        Optional<TransactionRecord> saved = repository.findById("0000000000000031");
        assertThat(saved).isPresent();
        TransactionRecord row = saved.get();
        assertThat(row.getTypeCd()).isEqualTo("07");
        assertThat(row.getCatCd()).isEqualTo(1234);
        assertThat(row.getSource()).isEqualTo("POS");
        assertThat(row.getDescription()).isEqualTo("NEW TRANSACTION");
        assertThat(row.getAmount()).isEqualByComparingTo("-123.45");
        assertThat(row.getMerchantId()).isEqualTo(900000009L);
        assertThat(row.getMerchantName()).isEqualTo("NEW MERCHANT");
        assertThat(row.getMerchantCity()).isEqualTo("DENVER");
        assertThat(row.getMerchantZip()).isEqualTo("80202");
        // FR-A1: card_num was resolved from the account via the xref, not typed.
        assertThat(row.getCardNum()).isEqualTo("4111111111111111");
        assertThat(row.getOrigTs()).isEqualTo("2023-07-01");
        assertThat(row.getProcTs()).isEqualTo("2023-07-02");
    }

    @Test
    void frA2_cardEntered_resolvesAccountAndWritesRow() {
        TransactionAddRequest r = validRequest();
        r.setAccountId("");
        r.setCardNumber("4222222222222222");

        TransactionAddResponse response = service.add(r);

        assertThat(response.getTranId()).isEqualTo("0000000000000031");
        TransactionRecord row = repository.findById("0000000000000031").orElseThrow();
        assertThat(row.getCardNum()).isEqualTo("4222222222222222");
    }

    @Test
    void frA6_incrementsRowCountByExactlyOne() {
        long before = repository.count();
        service.add(validRequest());
        assertThat(repository.count()).isEqualTo(before + 1);
    }

    @Test
    void frA3_bothKeysEmpty_noWrite() {
        TransactionAddRequest r = validRequest();
        r.setAccountId("");
        r.setCardNumber("");

        long before = repository.count();
        assertThatThrownBy(() -> service.add(r))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage("Account or Card Number must be entered...");
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void frA4_accountNotFound_noWrite() {
        TransactionAddRequest r = validRequest();
        r.setAccountId("99999999999");

        assertThatThrownBy(() -> service.add(r))
                .isInstanceOf(CardXrefNotFoundException.class)
                .hasMessage("Account ID NOT found...");
    }

    @Test
    void frA11_confirmBlank_noWrite() {
        TransactionAddRequest r = validRequest();
        r.setConfirm("");

        long before = repository.count();
        assertThatThrownBy(() -> service.add(r))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage("Confirm to add this transaction...");
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void validationRunsBeforeConfirm_fieldErrorTakesPrecedence() {
        // Even with confirm blank, a bad data field errors first (key -> data -> confirm).
        TransactionAddRequest r = validRequest();
        r.setConfirm("");
        r.setAmount("bad");

        assertThatThrownBy(() -> service.add(r))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage("Amount should be in format -99999999.99");
    }
}
