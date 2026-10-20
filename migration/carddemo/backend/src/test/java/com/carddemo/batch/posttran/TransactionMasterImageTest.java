package com.carddemo.batch.posttran;

import com.carddemo.common.domain.TransactionRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-S11-031 - the TRANSACT.BKUP record is the CVTRA05Y TRAN-RECORD, LRECL 350. */
class TransactionMasterImageTest {

    @Test
    void rendersEveryFieldAtItsCvtra05yOffset() {
        String image = TransactionMasterImage.render(transaction(new BigDecimal("-12.34")));

        assertThat(image).hasSize(TransactionMasterImage.RECORD_LENGTH);
        assertThat(image.substring(0, 16)).isEqualTo("0000000000683580");   // TRAN-ID X(16)
        assertThat(image.substring(16, 18)).isEqualTo("01");                // TRAN-TYPE-CD X(02)
        assertThat(image.substring(18, 22)).isEqualTo("0001");              // TRAN-CAT-CD 9(04)
        assertThat(image.substring(22, 32)).isEqualTo("POS TERM  ");        // TRAN-SOURCE X(10)
        assertThat(image.substring(32, 132).trim()).isEqualTo("Purchase at Abshire-Lowe");
        assertThat(image.substring(132, 143)).isEqualTo("0000000123M");     // TRAN-AMT S9(09)V99
        assertThat(image.substring(143, 152)).isEqualTo("800000000");       // TRAN-MERCHANT-ID 9(09)
        assertThat(image.substring(152, 202).trim()).isEqualTo("Abshire-Lowe");
        assertThat(image.substring(202, 252).trim()).isEqualTo("North Enoshaven");
        assertThat(image.substring(252, 262)).isEqualTo("72112     ");
        assertThat(image.substring(262, 278)).isEqualTo("4859452612877065");
        assertThat(image.substring(278, 304)).isEqualTo("2022-06-10 19:27:53.000000");
        assertThat(image.substring(304, 330)).isEqualTo("2022-06-15 08:00:00.000000");
        assertThat(image.substring(330)).isBlank().hasSize(20);              // FILLER X(20)
    }

    @Test
    void positiveAmountsUseThePositiveOverpunch() {
        assertThat(TransactionMasterImage.render(transaction(new BigDecimal("50.47"))).substring(132, 143))
                .isEqualTo("0000000504G");
    }

    private static TransactionRecord transaction(BigDecimal amount) {
        TransactionRecord transaction = new TransactionRecord();
        transaction.setId("0000000000683580");
        transaction.setTypeCd("01");
        transaction.setCatCd(1);
        transaction.setSource("POS TERM");
        transaction.setDescription("Purchase at Abshire-Lowe");
        transaction.setAmount(amount);
        transaction.setMerchantId(800000000L);
        transaction.setMerchantName("Abshire-Lowe");
        transaction.setMerchantCity("North Enoshaven");
        transaction.setMerchantZip("72112");
        transaction.setCardNum("4859452612877065");
        transaction.setOrigTs("2022-06-10 19:27:53.000000");
        transaction.setProcTs("2022-06-15 08:00:00.000000");
        return transaction;
    }
}
