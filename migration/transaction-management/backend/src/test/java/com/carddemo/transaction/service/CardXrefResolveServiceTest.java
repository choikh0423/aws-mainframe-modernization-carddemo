package com.carddemo.transaction.service;

import com.carddemo.transaction.dto.CardXrefResolveResponse;
import com.carddemo.transaction.exception.CardXrefNotFoundException;
import com.carddemo.transaction.exception.TransactionValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-A1..FR-A5 coverage for {@link CardXrefResolveService} against the seeded
 * card_xref rows (data.sql). Traces to COTRN02C's VALIDATE-INPUT-KEY-FIELDS +
 * READ-CXACAIX-FILE / READ-CCXREF-FILE (COTRN02C.cbl:193-230, 576-637).
 *
 * <p>Uses the plain {@code @SpringBootTest} config so it reuses the cached
 * default context (the in-memory H2 with DB_CLOSE_DELAY=-1 is shared across the
 * suite; a divergent context would re-run data.sql and hit duplicate keys).
 */
@SpringBootTest
class CardXrefResolveServiceTest {

    @Autowired
    private CardXrefResolveService service;

    @Test
    void frA1_accountResolvesCard() {
        CardXrefResolveResponse r = service.resolve("10000000001", "");

        assertThat(r.getAccountId()).isEqualTo("10000000001");
        assertThat(r.getCardNumber()).isEqualTo("4111111111111111");
    }

    @Test
    void frA1_shortAccountIsZeroPaddedToKeyWidth() {
        CardXrefResolveResponse r = service.resolve("10000000003", null);

        assertThat(r.getAccountId()).isEqualTo("10000000003");
        assertThat(r.getCardNumber()).isEqualTo("4333333333333333");
    }

    @Test
    void frA2_cardResolvesAccount() {
        CardXrefResolveResponse r = service.resolve("", "4222222222222222");

        assertThat(r.getCardNumber()).isEqualTo("4222222222222222");
        assertThat(r.getAccountId()).isEqualTo("10000000002");
    }

    @Test
    void frA1_accountTakesPriorityOverCard() {
        // Both entered: account drives the read and overwrites the card (cbl:196-209).
        CardXrefResolveResponse r = service.resolve("10000000001", "9999999999999999");

        assertThat(r.getCardNumber()).isEqualTo("4111111111111111");
    }

    @Test
    void frA3_bothEmpty_throwsMustBeEntered() {
        assertThatThrownBy(() -> service.resolve("", ""))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage("Account or Card Number must be entered...");
    }

    @Test
    void frA4_accountNotFound() {
        assertThatThrownBy(() -> service.resolve("99999999999", ""))
                .isInstanceOf(CardXrefNotFoundException.class)
                .hasMessage("Account ID NOT found...");
    }

    @Test
    void frA5_cardNotFound() {
        assertThatThrownBy(() -> service.resolve("", "1234567890123456"))
                .isInstanceOf(CardXrefNotFoundException.class)
                .hasMessage("Card Number NOT found...");
    }

    @Test
    void accountNotNumeric_throwsMustBeNumeric() {
        assertThatThrownBy(() -> service.resolve("100000000AB", ""))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage("Account ID must be Numeric...");
    }

    @Test
    void cardNotNumeric_throwsMustBeNumeric() {
        assertThatThrownBy(() -> service.resolve("", "41111111111111AB"))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage("Card Number must be Numeric...");
    }
}
