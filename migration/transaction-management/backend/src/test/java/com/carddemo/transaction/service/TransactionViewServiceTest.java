package com.carddemo.transaction.service;

import com.carddemo.transaction.dto.TransactionViewResponse;
import com.carddemo.transaction.exception.EmptyTranIdException;
import com.carddemo.transaction.exception.TransactionNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-V1/FR-V2/FR-V3 coverage for {@link TransactionViewService} against the
 * H2 DB seeded by schema.sql + data.sql. Traces to COTRN01C's PROCESS-ENTER-KEY
 * / READ-TRANSACT-FILE behaviour.
 */
@SpringBootTest
class TransactionViewServiceTest {

    @Autowired
    private TransactionViewService service;

    @Test
    void frV1_found_returnsAllDisplayFields() {
        TransactionViewResponse r = service.view("0000000000000001");

        assertThat(r.getId()).isEqualTo("0000000000000001");
        assertThat(r.getCardNum()).isEqualTo("4111111111111111");
        assertThat(r.getTypeCd()).isEqualTo("01");
        assertThat(r.getCatCd()).isEqualTo(1000);
        assertThat(r.getSource()).isEqualTo("POS");
        assertThat(r.getDescription()).isEqualTo("GROCERY PURCHASE");
        assertThat(r.getAmount()).isEqualByComparingTo("13.75");
        assertThat(r.getAmountDisplay()).isEqualTo("+00000013.75");
        assertThat(r.getOrigTs()).isEqualTo("2023-06-01-10.15.31.000000");
        assertThat(r.getProcTs()).isEqualTo("2023-06-01-23.59.51.000000");
        assertThat(r.getMerchantId()).isEqualTo(900000001L);
        assertThat(r.getMerchantName()).isEqualTo("ACME SUPERMARKET");
        assertThat(r.getMerchantCity()).isEqualTo("NEW YORK");
        assertThat(r.getMerchantZip()).isEqualTo("10001");
    }

    @Test
    void frV1_negativeAmount_editedWithMinusSign() {
        TransactionViewResponse r = service.view("0000000000000004");

        assertThat(r.getAmount()).isEqualByComparingTo("-25.00");
        assertThat(r.getAmountDisplay()).isEqualTo("-00000025.00");
    }

    @Test
    void frV2_notFound_throwsWithLegacyMessage() {
        assertThatThrownBy(() -> service.view("9999999999999999"))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessage("Transaction ID NOT found...");
    }

    @Test
    void frV3_emptyId_throwsWithLegacyMessage() {
        assertThatThrownBy(() -> service.view(""))
                .isInstanceOf(EmptyTranIdException.class)
                .hasMessage("Tran ID can NOT be empty...");
    }

    @Test
    void frV3_blankId_throwsWithLegacyMessage() {
        assertThatThrownBy(() -> service.view("   "))
                .isInstanceOf(EmptyTranIdException.class)
                .hasMessage("Tran ID can NOT be empty...");
    }
}
