package com.carddemo.batch.posttran;

import com.carddemo.common.batch.FixedWidthRecord;
import com.carddemo.common.domain.DailyTransactionRecord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-S11-018 - the reject record repeats the daily transaction exactly as it was
 * read (CBTRN02C.cbl:447), so rendering a DALYTRAN row has to give back the
 * bytes DATALOAD parsed it from.
 */
class DailyTransactionImageTest {

    private static final Path DALYTRAN = Path.of("../../../app/data/ASCII/dailytran.txt");

    @Test
    void reproducesTheInputRecordByteForByte() throws IOException {
        List<String> lines = Files.readAllLines(DALYTRAN);

        assertThat(lines).hasSize(300);
        for (String line : lines) {
            assertThat(line).hasSize(DailyTransactionImage.RECORD_LENGTH);
            assertThat(DailyTransactionImage.render(parse(line)))
                    .as("record %s", line.substring(0, 16))
                    .isEqualTo(line);
        }
    }

    @Test
    void writesTheAmountAsAZonedDecimalWithAnOverpunchedSign() {
        assertThat(DailyTransactionImage.zoned(new BigDecimal("13.75"), 11, 2)).isEqualTo("0000000137E");
        assertThat(DailyTransactionImage.zoned(new BigDecimal("-13.75"), 11, 2)).isEqualTo("0000000137N");
        assertThat(DailyTransactionImage.zoned(new BigDecimal("0.00"), 11, 2)).isEqualTo("0000000000{");
        assertThat(DailyTransactionImage.zoned(new BigDecimal("-0.01"), 11, 2)).isEqualTo("0000000000J");
    }

    @Test
    void padsAlphanumericFieldsRightAndNumericFieldsLeft() {
        assertThat(DailyTransactionImage.text("AB", 5)).isEqualTo("AB   ");
        assertThat(DailyTransactionImage.text(null, 3)).isEqualTo("   ");
        assertThat(DailyTransactionImage.digits(42, 6)).isEqualTo("000042");
        assertThat(DailyTransactionImage.digits(null, 4)).isEqualTo("0000");
    }

    /** The DALYTRAN mapping DATALOAD uses (CVTRA06Y). */
    private static DailyTransactionRecord parse(String line) {
        FixedWidthRecord r = new FixedWidthRecord(line, DailyTransactionImage.RECORD_LENGTH);
        DailyTransactionRecord daily = new DailyTransactionRecord();
        daily.setId(r.text(0, 16));
        daily.setTypeCd(r.text(16, 2));
        daily.setCatCd((int) r.number(18, 4));
        daily.setSource(r.text(22, 10));
        daily.setDescription(r.text(32, 100));
        daily.setAmount(r.signed(132, 11, 2));
        daily.setMerchantId(r.number(143, 9));
        daily.setMerchantName(r.text(152, 50));
        daily.setMerchantCity(r.text(202, 50));
        daily.setMerchantZip(r.text(252, 10));
        daily.setCardNum(r.text(262, 16));
        daily.setOrigTs(r.text(278, 26));
        daily.setProcTs(r.text(304, 26));
        return daily;
    }
}
