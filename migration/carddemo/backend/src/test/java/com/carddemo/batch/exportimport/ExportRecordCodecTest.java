package com.carddemo.batch.exportimport;

import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CardRecord;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.CustomerRecord;
import com.carddemo.common.domain.TransactionRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 500-byte export record: its header (type, timestamp, sequence, branch,
 * region) and the per-type data areas of CVEXPORT.cpy.
 */
class ExportRecordCodecTest {

    private static final String TIMESTAMP = "2026-09-09 19:13:41.00";

    @Test
    void everyRecordTypeIsFiveHundredBytes() {
        assertThat(ExportRecordCodec.customerRecord(ExportImportFixtures.customer(), TIMESTAMP)).hasSize(500);
        assertThat(ExportRecordCodec.accountRecord(ExportImportFixtures.account(), TIMESTAMP)).hasSize(500);
        assertThat(ExportRecordCodec.cardXrefRecord(ExportImportFixtures.cardXref(), TIMESTAMP)).hasSize(500);
        assertThat(ExportRecordCodec.transactionRecord(ExportImportFixtures.transaction(), TIMESTAMP)).hasSize(500);
        assertThat(ExportRecordCodec.cardRecord(ExportImportFixtures.card(), TIMESTAMP)).hasSize(500);
    }

    @Test
    void theHeaderCarriesTypeTimestampBranchAndRegion() {
        byte[] record = ExportRecordCodec.customerRecord(ExportImportFixtures.customer(), TIMESTAMP);

        assertThat(ExportRecordCodec.recordType(record)).isEqualTo('C');
        assertThat(text(record, 1, 26)).isEqualTo(TIMESTAMP + "    ");
        assertThat(text(record, 31, 4)).isEqualTo("0001");
        assertThat(text(record, 35, 5)).isEqualTo("NORTH");
    }

    @Test
    void eachRecordTypeGetsItsOwnTypeByte() {
        assertThat(ExportRecordCodec.recordType(
                ExportRecordCodec.accountRecord(ExportImportFixtures.account(), TIMESTAMP))).isEqualTo('A');
        assertThat(ExportRecordCodec.recordType(
                ExportRecordCodec.cardXrefRecord(ExportImportFixtures.cardXref(), TIMESTAMP))).isEqualTo('X');
        assertThat(ExportRecordCodec.recordType(
                ExportRecordCodec.transactionRecord(ExportImportFixtures.transaction(), TIMESTAMP))).isEqualTo('T');
        assertThat(ExportRecordCodec.recordType(
                ExportRecordCodec.cardRecord(ExportImportFixtures.card(), TIMESTAMP))).isEqualTo('D');
    }

    @Test
    void theSequenceNumberIsAFourByteBinaryStampedAtWriteTime() {
        byte[] record = ExportRecordCodec.cardRecord(ExportImportFixtures.card(), TIMESTAMP);
        assertThat(ExportRecordCodec.sequenceNumber(record)).isZero();

        ExportRecordCodec.putSequenceNumber(record, 258);

        assertThat(record[27]).isZero();
        assertThat(record[28]).isZero();
        assertThat(record[29]).isEqualTo((byte) 0x01);
        assertThat(record[30]).isEqualTo((byte) 0x02);
        assertThat(ExportRecordCodec.sequenceNumber(record)).isEqualTo(258);
    }

    @Test
    void theDataAreaIsSpaceFilledBeyondTheMappedFields() {
        byte[] record = ExportRecordCodec.cardXrefRecord(ExportImportFixtures.cardXref(), TIMESTAMP);

        assertThat(text(record, 40 + 33, 500 - 40 - 33)).isBlank();
    }

    @Test
    void customerFieldsLandOnTheirCopybookOffsets() {
        CustomerRecord customer = ExportImportFixtures.customer();

        byte[] record = ExportRecordCodec.customerRecord(customer, TIMESTAMP);

        assertThat(MainframeFieldCodec.getBinary(record, 40, 4)).isEqualTo(customer.getCustId());
        assertThat(text(record, 44, 25)).isEqualTo("JOHN                     ");
        assertThat(text(record, 94, 25)).isEqualTo("DOE                      ");
        assertThat(text(record, 269, 2)).isEqualTo("NY");
        assertThat(MainframeFieldCodec.getZoned(record, 314, 9)).isEqualTo(123456789L);
        assertThat(MainframeFieldCodec.getPacked(record, 364, 2, 0))
                .isEqualByComparingTo(BigDecimal.valueOf(789));
    }

    @Test
    void accountAmountsKeepTheirScaleAndSign() {
        AccountRecord account = ExportImportFixtures.account();

        byte[] record = ExportRecordCodec.accountRecord(account, TIMESTAMP);

        assertThat(MainframeFieldCodec.getZoned(record, 40, 11)).isEqualTo(account.getAcctId());
        assertThat(MainframeFieldCodec.getPacked(record, 52, 7, 2)).isEqualByComparingTo(new BigDecimal("-1234.56"));
        assertThat(MainframeFieldCodec.getZonedSigned(record, 59, 12, 2)).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(MainframeFieldCodec.getBinaryDecimal(record, 120, 8, 2)).isEqualByComparingTo(new BigDecimal("42.13"));
    }

    @Test
    void transactionAmountsAndIdsLandOnTheirCopybookOffsets() {
        TransactionRecord transaction = ExportImportFixtures.transaction();

        byte[] record = ExportRecordCodec.transactionRecord(transaction, TIMESTAMP);

        assertThat(text(record, 40, 16)).isEqualTo(transaction.getId());
        assertThat(MainframeFieldCodec.getPacked(record, 172, 6, 2)).isEqualByComparingTo(new BigDecimal("13.75"));
        assertThat(MainframeFieldCodec.getBinary(record, 178, 4)).isEqualTo(transaction.getMerchantId());
    }

    @Test
    void cardFieldsLandOnTheirCopybookOffsets() {
        CardRecord card = ExportImportFixtures.card();

        byte[] record = ExportRecordCodec.cardRecord(card, TIMESTAMP);

        assertThat(text(record, 40, 16)).isEqualTo(card.getCardNum());
        assertThat(MainframeFieldCodec.getBinary(record, 56, 8)).isEqualTo(card.getAcctId());
        assertThat(MainframeFieldCodec.getBinary(record, 64, 2)).isEqualTo(card.getCvvCd().longValue());
        assertThat(text(record, 126, 1)).isEqualTo("Y");
    }

    @Test
    void xrefFieldsLandOnTheirCopybookOffsets() {
        CardXrefRecord xref = ExportImportFixtures.cardXref();

        byte[] record = ExportRecordCodec.cardXrefRecord(xref, TIMESTAMP);

        assertThat(text(record, 40, 16)).isEqualTo(xref.getCardNum());
        assertThat(MainframeFieldCodec.getZoned(record, 56, 9)).isEqualTo(xref.getCustId());
        assertThat(MainframeFieldCodec.getBinary(record, 65, 8)).isEqualTo(xref.getAcctId());
    }

    private static String text(byte[] record, int offset, int length) {
        return new String(record, offset, length, StandardCharsets.ISO_8859_1);
    }
}
