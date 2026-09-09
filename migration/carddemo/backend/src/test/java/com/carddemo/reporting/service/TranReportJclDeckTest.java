package com.carddemo.reporting.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-R33 / FR-R34 — the internal-reader deck CORPT00C writes to TDQ JOBS,
 * pinned against {@code JOB-DATA} (cbl:81-127).
 */
class TranReportJclDeckTest {

    private final List<String> deck = TranReportJclDeck.build("2023-07-01", "2023-07-31");

    @Test
    @DisplayName("FR-R34: 17 records reach the queue, the /*EOF terminator included")
    void writesSeventeenRecords() {
        assertThat(deck).hasSize(17);
        assertThat(deck.get(16)).startsWith("/*EOF");
    }

    @Test
    @DisplayName("FR-R33: every record is exactly 80 characters, as PIC X(80) demands")
    void everyRecordIs80Characters() {
        assertThat(deck).allSatisfy(line -> assertThat(line).hasSize(80));
    }

    @Test
    @DisplayName("FR-R33: the job card, JCLLIB and the TRANREPT proc step are reproduced")
    void reproducesTheJobCard() {
        assertThat(deck.get(0).trim())
                .isEqualTo("//TRNRPT00 JOB 'TRAN REPORT',CLASS=A,MSGCLASS=0,");
        assertThat(deck.get(1).trim()).isEqualTo("// NOTIFY=&SYSUID");
        assertThat(deck.get(3).trim())
                .isEqualTo("//JOBLIB JCLLIB ORDER=('AWS.M2.CARDDEMO.PROC')");
        assertThat(deck.get(5).trim()).isEqualTo("//STEP10 EXEC PROC=TRANREPT");
    }

    @Test
    @DisplayName("FR-R33: the SYMNAMES fields keep their offsets and types")
    void reproducesTheSymnames() {
        assertThat(deck.get(7).trim()).isEqualTo("//STEP05R.SYMNAMES DD *");
        assertThat(deck.get(8).trim()).isEqualTo("TRAN-CARD-NUM,263,16,ZD");
        assertThat(deck.get(9).trim()).isEqualTo("TRAN-PROC-DT,305,10,CH");
    }

    @Test
    @DisplayName("FR-R33: the chosen range is substituted into both SYMNAMES parameters")
    void substitutesTheRangeIntoSymnames() {
        assertThat(deck.get(10)).startsWith("PARM-START-DATE,C'2023-07-01'");
        assertThat(deck.get(11)).startsWith("PARM-END-DATE,C'2023-07-31'");
    }

    @Test
    @DisplayName("FR-R33: and into the DATEPARM instream data, separated by one blank")
    void substitutesTheRangeIntoDateparm() {
        assertThat(deck.get(13).trim()).isEqualTo("//STEP10R.DATEPARM DD *");
        assertThat(deck.get(14)).startsWith("2023-07-01 2023-07-31");
        assertThat(deck.get(14).substring(21)).isBlank();
    }

    @Test
    @DisplayName("a short date is padded into its X(10) slot, keeping the record aligned")
    void padsAShortDate() {
        List<String> padded = TranReportJclDeck.build("2023-7-1", "2023-07-31");

        assertThat(padded.get(10)).startsWith("PARM-START-DATE,C'2023-7-1  '");
        assertThat(padded.get(10)).hasSize(80);
    }
}
