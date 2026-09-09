package com.carddemo.batch.posttran;

import com.carddemo.common.domain.DailyTransactionRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-S11-018, FR-S11-019 - 2500-WRITE-REJECT-REC (CBTRN02C.cbl:176-182, 446-451). */
class RejectRecordTest {

    @Test
    void rendersA430CharacterRecord() {
        String rejected = RejectRecord.render(daily(), overlimit());

        assertThat(rejected).hasSize(RejectRecord.RECORD_LENGTH);
        assertThat(rejected).startsWith(DailyTransactionImage.render(daily()));
    }

    @Test
    void rendersTheTrailerAsReasonAndPaddedDescription() {
        String trailer = RejectRecord.trailer(overlimit());

        assertThat(trailer).hasSize(RejectRecord.TRAILER_LENGTH);
        assertThat(trailer).startsWith("0102OVERLIMIT TRANSACTION");
        assertThat(trailer.substring(4)).isEqualTo(
                "OVERLIMIT TRANSACTION" + " ".repeat(76 - "OVERLIMIT TRANSACTION".length()));
    }

    @Test
    void zeroPadsEveryReasonCodeToFourDigits() {
        assertThat(RejectRecord.trailer(new ValidationResult(100, "INVALID CARD NUMBER FOUND", null, null)))
                .startsWith("0100INVALID CARD NUMBER FOUND ");
        assertThat(RejectRecord.trailer(new ValidationResult(103,
                "TRANSACTION RECEIVED AFTER ACCT EXPIRATION", null, null)))
                .startsWith("0103TRANSACTION RECEIVED AFTER ACCT EXPIRATION ");
    }

    private static ValidationResult overlimit() {
        return new ValidationResult(TransactionValidationService.REASON_OVERLIMIT,
                TransactionValidationService.OVERLIMIT_TRANSACTION, null, null);
    }

    private static DailyTransactionRecord daily() {
        DailyTransactionRecord daily = new DailyTransactionRecord();
        daily.setId("0000000000000001");
        daily.setTypeCd("01");
        daily.setCatCd(5001);
        daily.setSource("POS TERM");
        daily.setDescription("Groceries");
        daily.setAmount(new BigDecimal("13.75"));
        daily.setMerchantId(123456789L);
        daily.setMerchantName("MERCHANT");
        daily.setMerchantCity("CITY");
        daily.setMerchantZip("12345");
        daily.setCardNum("4111111111111111");
        daily.setOrigTs("2024-03-07-09.04.05.120000");
        daily.setProcTs("");
        return daily;
    }
}
