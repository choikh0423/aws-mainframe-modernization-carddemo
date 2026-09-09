package com.carddemo.card.service;

import com.carddemo.card.dto.CardListRow;
import com.carddemo.card.dto.CardSelectionRequest;
import com.carddemo.card.dto.CardSelectionResponse;
import com.carddemo.card.exception.CardValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-L13..FR-L16 coverage for the CCLI selection flags
 * (COCRDLIC.cbl:1075-1121 2250-EDIT-ARRAY, 517-569 the XCTL targets).
 */
class CardSelectionServiceTest {

    private final CardSelectionService service = new CardSelectionService();

    private static final List<CardListRow> ROWS = List.of(
            new CardListRow("00000000050", "0500024453765740", "Y"),
            new CardListRow("00000000027", "0683586198171516", "Y"),
            new CardListRow("00000000002", "0923877193247330", "Y"));

    private static CardSelectionRequest request(List<String> flags) {
        CardSelectionRequest request = new CardSelectionRequest();
        request.setFlags(flags);
        request.setRows(ROWS);
        return request;
    }

    @Test
    void frL13_sRoutesToTheDetailProgramWithTheRowKeys() {
        CardSelectionResponse target = service.select(request(List.of("", "S", "")));

        assertThat(target.getProgram()).isEqualTo("COCRDSLC");
        assertThat(target.getTranId()).isEqualTo("CCDL");
        assertThat(target.getMapset()).isEqualTo("COCRDSL");
        assertThat(target.getMap()).isEqualTo("CCRDSLA");
        assertThat(target.getAcctId()).isEqualTo("00000000027");
        assertThat(target.getCardNum()).isEqualTo("0683586198171516");
    }

    @Test
    void frL14_uRoutesToTheUpdateProgram() {
        CardSelectionResponse target = service.select(request(List.of("U", "", "")));

        assertThat(target.getProgram()).isEqualTo("COCRDUPC");
        assertThat(target.getTranId()).isEqualTo("CCUP");
        assertThat(target.getCardNum()).isEqualTo("0500024453765740");
    }

    @Test
    void frL13_lowerCaseFlagsAreAccepted() {
        assertThat(service.select(request(List.of("s", "", ""))).getProgram()).isEqualTo("COCRDSLC");
    }

    @Test
    void frL15_twoSelectionsAreRejected() {
        assertThatThrownBy(() -> service.select(request(List.of("S", "U", ""))))
                .isInstanceOf(CardValidationException.class)
                .hasMessage("PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE");
    }

    @Test
    void frL16_anyOtherFlagIsAnInvalidActionCode() {
        assertThatThrownBy(() -> service.select(request(List.of("", "X", ""))))
                .hasMessage("INVALID ACTION CODE");
    }

    @Test
    void frL15_theMultiSelectMessageWinsOverALaterInvalidCode() {
        assertThatThrownBy(() -> service.select(request(List.of("S", "U", "X"))))
                .hasMessage("PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE");
    }

    @Test
    void frL1_enterWithNothingSelectedJustRedisplaysTheList() {
        assertThat(service.select(request(List.of("", "", "")))).isNull();
    }
}
