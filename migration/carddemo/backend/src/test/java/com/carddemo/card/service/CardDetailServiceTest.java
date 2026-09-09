package com.carddemo.card.service;

import com.carddemo.card.dto.CardDetailResponse;
import com.carddemo.card.exception.CardNotFoundException;
import com.carddemo.card.exception.CardValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-D1, FR-D5, FR-D8, FR-D9 coverage for {@link CardDetailService} against the seeded H2
 * database. Traces to COCRDSLC 2200-EDIT-MAP-INPUTS and 9000-READ-CARD-FILE
 * (COCRDSLC.cbl:608-805).
 */
@SpringBootTest
class CardDetailServiceTest {

    /** carddata.txt: card 0923877193247330, account 2, expiry 2024-08-11. */
    private static final String CARD = "0923877193247330";
    private static final String ACCOUNT = "00000000002";

    @Autowired
    private CardDetailService service;

    @Test
    void frD1_aValidPairDisplaysTheCard() {
        CardDetailResponse detail = service.detail(ACCOUNT, CARD);

        assertThat(detail.getAcctId()).isEqualTo(ACCOUNT);
        assertThat(detail.getCardNum()).isEqualTo(CARD);
        assertThat(detail.getEmbossedName()).isEqualTo("Enrico Rosenbaum");
        assertThat(detail.getActiveStatus()).isEqualTo("Y");
        // CARD-EXPIRAION-DATE(6:2) / (1:4) drive the EXPMON / EXPYEAR fields.
        assertThat(detail.getExpiryMonth()).isEqualTo("08");
        assertThat(detail.getExpiryYear()).isEqualTo("2024");
        assertThat(detail.getInfoMessage()).isEqualTo("   Displaying requested details");
    }

    @Test
    void frD9_anAccountThatDoesNotOwnTheCardStillDisplaysIt() {
        // 9000-READ-CARD-FILE is keyed on the card number alone; the account is
        // validated and echoed but never constrains the read (quirk Q-3).
        CardDetailResponse detail = service.detail("00000000050", CARD);

        assertThat(detail.getCardNum()).isEqualTo(CARD);
        assertThat(detail.getAcctId()).isEqualTo("00000000050");
        assertThat(detail.getEmbossedName()).isEqualTo("Enrico Rosenbaum");
    }

    @Test
    void frD8_anUnknownCardGivesTheNotFoundMessage() {
        assertThatThrownBy(() -> service.detail(ACCOUNT, "9999999999999999"))
                .isInstanceOf(CardNotFoundException.class)
                .hasMessage("Did not find cards for this search condition");
    }

    @Test
    void frD5_theKeyEditsRunBeforeTheRead() {
        assertThatThrownBy(() -> service.detail("*", "*"))
                .isInstanceOf(CardValidationException.class)
                .hasMessage("No input received");
    }
}
