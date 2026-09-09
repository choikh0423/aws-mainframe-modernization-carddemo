package com.carddemo.batch.intcalc;

import com.carddemo.common.domain.TransactionRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-I19, FR-C5: the 350-byte CVTRA05Y image of a transaction. */
class TransactionRecordLineTest {

    @Test
    void formatsA350ByteCvtra05yRecord() {
        String line = TransactionRecordLine.format(interestTransaction(new BigDecimal("16.65")));

        assertThat(line).hasSize(350);
        field(line, "TRAN-ID", 0, 16).isEqualTo("2022071800000001");
        field(line, "TRAN-TYPE-CD", 16, 2).isEqualTo("01");
        field(line, "TRAN-CAT-CD", 18, 4).isEqualTo("0005");
        field(line, "TRAN-SOURCE", 22, 10).isEqualTo("System    ");
        field(line, "TRAN-DESC", 32, 20).isEqualTo("Int. for a/c 0000000");
        field(line, "TRAN-AMT", 132, 11).isEqualTo("0000000166E");
        field(line, "TRAN-MERCHANT-ID", 143, 9).isEqualTo("000000000");
        field(line, "TRAN-MERCHANT-NAME", 152, 50).isBlank();
        field(line, "TRAN-CARD-NUM", 262, 16).isEqualTo("4111111111111111");
        field(line, "TRAN-ORIG-TS", 278, 26).isEqualTo("2022-07-18-10.04.05.670000");
        field(line, "TRAN-PROC-TS", 304, 26).isEqualTo("2022-07-18-10.04.05.670000");
        field(line, "FILLER", 330, 20).isBlank();
    }

    /** A negative amount carries its sign as an overpunch on the last digit. */
    @Test
    void writesNegativeAmountsWithANegativeOverpunch() {
        String line = TransactionRecordLine.format(interestTransaction(new BigDecimal("-16.65")));

        field(line, "TRAN-AMT", 132, 11).isEqualTo("0000000166N");
    }

    @Test
    void writesZeroAmountsAsAPositiveZero() {
        String line = TransactionRecordLine.format(interestTransaction(new BigDecimal("0.00")));

        field(line, "TRAN-AMT", 132, 11).isEqualTo("0000000000{");
    }

    @Test
    void roundTripsEveryField() {
        TransactionRecord original = interestTransaction(new BigDecimal("-1234567.89"));

        TransactionRecord parsed = TransactionRecordLine.parse(TransactionRecordLine.format(original));

        assertThat(parsed.getId()).isEqualTo(original.getId());
        assertThat(parsed.getTypeCd()).isEqualTo(original.getTypeCd());
        assertThat(parsed.getCatCd()).isEqualTo(original.getCatCd());
        assertThat(parsed.getSource()).isEqualTo(original.getSource());
        assertThat(parsed.getDescription()).isEqualTo(original.getDescription());
        assertThat(parsed.getAmount()).isEqualByComparingTo(original.getAmount());
        assertThat(parsed.getMerchantId()).isEqualTo(original.getMerchantId());
        assertThat(parsed.getMerchantName()).isEqualTo(original.getMerchantName());
        assertThat(parsed.getMerchantCity()).isEqualTo(original.getMerchantCity());
        assertThat(parsed.getMerchantZip()).isEqualTo(original.getMerchantZip());
        assertThat(parsed.getCardNum()).isEqualTo(original.getCardNum());
        assertThat(parsed.getOrigTs()).isEqualTo(original.getOrigTs());
        assertThat(parsed.getProcTs()).isEqualTo(original.getProcTs());
    }

    /** FR-C3: the sort key DFSORT compares is bytes 1-16. */
    @Test
    void exposesTheSixteenByteSortKey() {
        String line = TransactionRecordLine.format(interestTransaction(BigDecimal.ZERO.setScale(2)));

        assertThat(TransactionRecordLine.tranIdOf(line)).isEqualTo("2022071800000001");
    }

    private static org.assertj.core.api.AbstractStringAssert<?> field(
            String line, String name, int offset, int length) {
        return org.assertj.core.api.Assertions.assertThat(line.substring(offset, offset + length))
                .describedAs(name);
    }

    private static TransactionRecord interestTransaction(BigDecimal amount) {
        TransactionRecord tran = new TransactionRecord();
        tran.setId("2022071800000001");
        tran.setTypeCd("01");
        tran.setCatCd(5);
        tran.setSource("System");
        tran.setDescription("Int. for a/c 00000000011");
        tran.setAmount(amount);
        tran.setMerchantId(0L);
        tran.setMerchantName("");
        tran.setMerchantCity("");
        tran.setMerchantZip("");
        tran.setCardNum("4111111111111111");
        tran.setOrigTs("2022-07-18-10.04.05.670000");
        tran.setProcTs("2022-07-18-10.04.05.670000");
        return tran;
    }
}
