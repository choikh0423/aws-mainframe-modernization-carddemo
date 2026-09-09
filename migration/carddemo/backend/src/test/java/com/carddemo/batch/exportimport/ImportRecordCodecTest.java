package com.carddemo.batch.exportimport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CBIMPORT's EVALUATE and its five mapping paragraphs: the type byte decides the
 * target file, and each target record comes out at its copybook length with the
 * export record's binary and packed fields rendered back as DISPLAY.
 */
class ImportRecordCodecTest {

    private static final String TIMESTAMP = "2026-09-09 19:13:41.00";

    @Test
    void aCustomerRecordGoesToCustoutAtFiveHundredBytes() {
        byte[] record = ExportRecordCodec.customerRecord(ExportImportFixtures.customer(), TIMESTAMP);

        ImportedRecord imported = ImportRecordCodec.decode(record);

        assertThat(imported.target()).isEqualTo(ImportTarget.CUSTOMER);
        assertThat(imported.line()).hasSize(500);
        assertThat(imported.line()).startsWith("000000001JOHN                     ");
        assertThat(imported.line().substring(279, 288)).isEqualTo("123456789");
        assertThat(imported.line().substring(329, 332)).isEqualTo("789");
    }

    @Test
    void anAccountRecordGoesToAcctoutWithSignedAmountsAsOverpunchedDisplay() {
        byte[] record = ExportRecordCodec.accountRecord(ExportImportFixtures.account(), TIMESTAMP);

        ImportedRecord imported = ImportRecordCodec.decode(record);

        assertThat(imported.target()).isEqualTo(ImportTarget.ACCOUNT);
        assertThat(imported.line()).hasSize(300);
        assertThat(imported.line()).startsWith("11111111111Y");
        // -1234.56 and +5000.00, both PIC S9(10)V99 DISPLAY.
        assertThat(imported.line().substring(12, 24)).isEqualTo("00000012345O");
        assertThat(imported.line().substring(24, 36)).isEqualTo("00000050000{");
        assertThat(imported.line().substring(90, 102)).isEqualTo("00000000421C");
    }

    @Test
    void anXrefRecordGoesToXrefoutAtFiftyBytes() {
        byte[] record = ExportRecordCodec.cardXrefRecord(ExportImportFixtures.cardXref(), TIMESTAMP);

        ImportedRecord imported = ImportRecordCodec.decode(record);

        assertThat(imported.target()).isEqualTo(ImportTarget.XREF);
        assertThat(imported.line()).hasSize(50);
        assertThat(imported.line()).startsWith("4111111111111111" + "000000001" + "11111111111");
    }

    @Test
    void aTransactionRecordGoesToTrnxoutAtThreeHundredAndFiftyBytes() {
        byte[] record = ExportRecordCodec.transactionRecord(ExportImportFixtures.transaction(), TIMESTAMP);

        ImportedRecord imported = ImportRecordCodec.decode(record);

        assertThat(imported.target()).isEqualTo(ImportTarget.TRANSACTION);
        assertThat(imported.line()).hasSize(350);
        assertThat(imported.line()).startsWith("000000000000000101" + "5411");
        assertThat(imported.line().substring(132, 143)).isEqualTo("0000000137E");
        assertThat(imported.line().substring(143, 152)).isEqualTo("999999999");
    }

    @Test
    void aCardRecordGoesToCardoutAtOneHundredAndFiftyBytes() {
        byte[] record = ExportRecordCodec.cardRecord(ExportImportFixtures.card(), TIMESTAMP);

        ImportedRecord imported = ImportRecordCodec.decode(record);

        assertThat(imported.target()).isEqualTo(ImportTarget.CARD);
        assertThat(imported.line()).hasSize(150);
        assertThat(imported.line()).startsWith("4111111111111111" + "11111111111" + "123");
        assertThat(imported.line().charAt(90)).isEqualTo('Y');
    }

    /** WHEN OTHER: no target file, so the caller writes an error record instead. */
    @Test
    void anUnrecognisedTypeByteDecodesToNothing() {
        byte[] record = ExportRecordCodec.cardRecord(ExportImportFixtures.card(), TIMESTAMP);
        record[0] = 'Z';

        assertThat(ImportRecordCodec.decode(record)).isNull();
    }
}
