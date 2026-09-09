package com.carddemo.batch.authpurge;

import com.carddemo.common.domain.PendingAuthDetailId;
import com.carddemo.common.domain.PendingAuthDetailRecord;
import com.carddemo.common.domain.PendingAuthSummaryRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-U1..FR-U4: the unload record layouts PAUDBUNL/DBUNLDGS write and PAUDBLOD
 * reads back — CIPAUSMY and CIPAUDTY field for field, in ASCII (BD-6).
 */
class PendingAuthSegmentFormatTest {

    private static PendingAuthSummaryRecord root() {
        PendingAuthSummaryRecord s = new PendingAuthSummaryRecord();
        s.setPaAcctId(1L);
        s.setPaCustId(1L);
        s.setPaAuthStatus("A");
        s.setPaAccountStatus1("AC");
        s.setPaAccountStatus2("  ");
        s.setPaAccountStatus3("  ");
        s.setPaAccountStatus4("  ");
        s.setPaAccountStatus5("  ");
        s.setPaCreditLimit(new BigDecimal("2020.00"));
        s.setPaCashLimit(new BigDecimal("1020.00"));
        s.setPaCreditBalance(new BigDecimal("262.55"));
        s.setPaCashBalance(BigDecimal.ZERO);
        s.setPaApprovedAuthCnt(6);
        s.setPaDeclinedAuthCnt(1);
        s.setPaApprovedAuthAmt(new BigDecimal("262.55"));
        s.setPaDeclinedAuthAmt(new BigDecimal("-500.00"));
        return s;
    }

    private static PendingAuthDetailRecord child() {
        PendingAuthDetailRecord d = new PendingAuthDetailRecord();
        d.setId(new PendingAuthDetailId(1L, 74984, 849999999L));
        d.setPaAuthOrigDate("250115");
        d.setPaAuthOrigTime("150000");
        d.setPaCardNum("9680294154603697");
        d.setPaAuthType("0100");
        d.setPaCardExpiryDate("2605");
        d.setPaMessageType("0100");
        d.setPaMessageSource("POS");
        d.setPaAuthIdCode("150000");
        d.setPaAuthRespCode("00");
        d.setPaAuthRespReason("0000");
        d.setPaProcessingCode(1000L);
        d.setPaTransactionAmt(new BigDecimal("13.75"));
        d.setPaApprovedAmt(new BigDecimal("13.75"));
        d.setPaMerchantCatagoryCode("5411");
        d.setPaAcqrCountryCode("840");
        d.setPaPosEntryMode(5);
        d.setPaMerchantId("900000001");
        d.setPaMerchantName("ACME SUPERMARKET");
        d.setPaMerchantCity("NEW YORK");
        d.setPaMerchantState("NY");
        d.setPaMerchantZip("10001");
        d.setPaTransactionId("PAUTH0000000001");
        d.setPaMatchStatus("P");
        d.setPaAuthFraud(" ");
        d.setPaFraudRptDate(" ");
        return d;
    }

    @Test
    void frU1_theRootRecordIsFixedWidthAndStartsWithTheZeroPaddedKey() {
        String line = PendingAuthSegmentFormat.formatRoot(root());

        assertThat(line).hasSize(PendingAuthSegmentFormat.ROOT_LENGTH);
        assertThat(line).startsWith("00000000001" + "000000001" + "A" + "AC");
        // Signed counts and implied-decimal amounts keep their sign, as PIC S9
        // fields do: 262.55 is +00000026255, -500.00 is -00000050000.
        assertThat(line).contains("+00000026255");
        assertThat(line).contains("-00000050000");
        assertThat(line).contains("+0006").contains("+0001");
    }

    @Test
    void frU2_theRootRecordRoundTripsThroughTheLoader() {
        PendingAuthSummaryRecord parsed =
                PendingAuthSegmentFormat.parseRoot(PendingAuthSegmentFormat.formatRoot(root()));

        assertThat(parsed.getPaAcctId()).isEqualTo(1L);
        assertThat(parsed.getPaCustId()).isEqualTo(1L);
        assertThat(parsed.getPaAuthStatus()).isEqualTo("A");
        assertThat(parsed.getPaAccountStatus1()).isEqualTo("AC");
        assertThat(parsed.getPaCreditLimit()).isEqualByComparingTo(new BigDecimal("2020.00"));
        assertThat(parsed.getPaCashLimit()).isEqualByComparingTo(new BigDecimal("1020.00"));
        assertThat(parsed.getPaCreditBalance()).isEqualByComparingTo(new BigDecimal("262.55"));
        assertThat(parsed.getPaCashBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(parsed.getPaApprovedAuthCnt()).isEqualTo(6);
        assertThat(parsed.getPaDeclinedAuthCnt()).isEqualTo(1);
        assertThat(parsed.getPaApprovedAuthAmt()).isEqualByComparingTo(new BigDecimal("262.55"));
        assertThat(parsed.getPaDeclinedAuthAmt()).isEqualByComparingTo(new BigDecimal("-500.00"));
    }

    @Test
    void frU3_theChildRecordIsFixedWidthAndCarriesItsRootKey() {
        String line = PendingAuthSegmentFormat.formatChild(child());

        assertThat(line).hasSize(PendingAuthSegmentFormat.CHILD_LENGTH);
        assertThat(line).startsWith("00000000001" + "74984" + "849999999");
        assertThat(PendingAuthSegmentFormat.childRootKey(line)).isEqualTo(1L);
        // PAUDBLOD skips a record whose root key is not numeric (PAUDBLOD.CBL:283).
        assertThat(PendingAuthSegmentFormat.childRootKey("ABCDEFGHIJK" + line.substring(11)))
                .isNull();
    }

    @Test
    void frU4_theChildRecordRoundTripsThroughTheLoader() {
        PendingAuthDetailRecord parsed =
                PendingAuthSegmentFormat.parseChild(PendingAuthSegmentFormat.formatChild(child()));

        assertThat(parsed.getId()).isEqualTo(new PendingAuthDetailId(1L, 74984, 849999999L));
        assertThat(parsed.getPaAuthOrigDate()).isEqualTo("250115");
        assertThat(parsed.getPaAuthOrigTime()).isEqualTo("150000");
        assertThat(parsed.getPaCardNum()).isEqualTo("9680294154603697");
        assertThat(parsed.getPaAuthType()).isEqualTo("0100");
        assertThat(parsed.getPaCardExpiryDate()).isEqualTo("2605");
        assertThat(parsed.getPaMessageType()).isEqualTo("0100");
        assertThat(parsed.getPaAuthIdCode()).isEqualTo("150000");
        assertThat(parsed.getPaAuthRespCode()).isEqualTo("00");
        assertThat(parsed.getPaAuthRespReason()).isEqualTo("0000");
        assertThat(parsed.getPaProcessingCode()).isEqualTo(1000L);
        assertThat(parsed.getPaTransactionAmt()).isEqualByComparingTo(new BigDecimal("13.75"));
        assertThat(parsed.getPaApprovedAmt()).isEqualByComparingTo(new BigDecimal("13.75"));
        assertThat(parsed.getPaMerchantCatagoryCode()).isEqualTo("5411");
        assertThat(parsed.getPaAcqrCountryCode()).isEqualTo("840");
        assertThat(parsed.getPaPosEntryMode()).isEqualTo(5);
        assertThat(parsed.getPaMerchantId()).isEqualTo("900000001");
        assertThat(parsed.getPaMerchantName()).isEqualTo("ACME SUPERMARKET");
        assertThat(parsed.getPaMerchantCity()).isEqualTo("NEW YORK");
        assertThat(parsed.getPaMerchantState()).isEqualTo("NY");
        assertThat(parsed.getPaMerchantZip()).isEqualTo("10001");
        assertThat(parsed.getPaTransactionId()).isEqualTo("PAUTH0000000001");
        assertThat(parsed.getPaMatchStatus()).isEqualTo("P");
    }
}
