package com.carddemo.card.service;

import com.carddemo.card.dto.CardListResponse;
import com.carddemo.card.exception.CardValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-L1..FR-L12 and FR-X1 coverage for {@link CardListService} against the H2 database
 * seeded from app/data/ASCII/carddata.txt (50 cards -> 8 pages of 7).
 * Traces to COCRDLIC's 9000-READ-FORWARD / 9100-READ-BACKWARDS /
 * 9500-FILTER-RECORDS and 1400-SETUP-MESSAGE.
 */
@SpringBootTest
class CardListServiceTest {

    private static final String PAGE1_FIRST = "0500024453765740";
    private static final String PAGE1_LAST = "1142167692878931";
    private static final String PAGE2_FIRST = "1561409106491600";
    private static final String LAST_CARD = "9805583408996588";

    @Autowired
    private CardListService service;

    private CardListResponse enter() {
        return service.list(null, null, "", null, null, null, null, null);
    }

    private CardListResponse forward(CardListResponse from) {
        return service.list(null, null, "F", from.getPageNumber(), from.getFirstCardNum(),
                from.getLastCardNum(), from.isNextPageExists(), from.isLastPageShown());
    }

    private CardListResponse backward(CardListResponse from) {
        return service.list(null, null, "B", from.getPageNumber(), from.getFirstCardNum(),
                from.getLastCardNum(), from.isNextPageExists(), from.isLastPageShown());
    }

    @Test
    void frL1_enterBrowsesSevenRowsFromTheTopOfCarddat() {
        CardListResponse page = enter();

        assertThat(page.getPageNumber()).isEqualTo(1);
        assertThat(page.getCount()).isEqualTo(7);
        assertThat(page.getRows().get(0).getCardNum()).isEqualTo(PAGE1_FIRST);
        assertThat(page.getRows().get(6).getCardNum()).isEqualTo(PAGE1_LAST);
        assertThat(page.isNextPageExists()).isTrue();
        assertThat(page.getErrorMessage()).isNull();
        assertThat(page.getInfoMessage()).isEqualTo("TYPE S FOR DETAIL, U TO UPDATE ANY RECORD");
    }

    @Test
    void frX1_theAccountNumberIsShownZeroPaddedTo11Digits() {
        // acct_id 50 on the first card: PIC 9(11) on the map.
        assertThat(enter().getRows().get(0).getAcctId()).isEqualTo("00000000050");
        assertThat(enter().getRows().get(0).getActiveStatus()).isEqualTo("Y");
    }

    @Test
    void frL7_pf8ShowsTheNextSevenAndAdvancesThePageNumber() {
        CardListResponse page2 = forward(enter());

        assertThat(page2.getPageNumber()).isEqualTo(2);
        assertThat(page2.getCount()).isEqualTo(7);
        assertThat(page2.getRows().get(0).getCardNum()).isEqualTo(PAGE2_FIRST);
    }

    @Test
    void frL10_pf7ReturnsToThePreviousSeven() {
        CardListResponse page1 = enter();
        CardListResponse page3 = forward(forward(page1));
        CardListResponse back = backward(page3);

        assertThat(back.getPageNumber()).isEqualTo(2);
        assertThat(back.getRows().get(0).getCardNum()).isEqualTo(PAGE2_FIRST);
        assertThat(backward(back).getRows()).extracting("cardNum")
                .containsExactlyElementsOf(page1.getRows().stream().map(r -> r.getCardNum()).toList());
    }

    @Test
    void frL11_pf7OnPageOneSaysThereAreNoPreviousPages() {
        CardListResponse page = backward(enter());

        assertThat(page.getPageNumber()).isEqualTo(1);
        assertThat(page.getRows().get(0).getCardNum()).isEqualTo(PAGE1_FIRST);
        assertThat(page.getErrorMessage()).isEqualTo("NO PREVIOUS PAGES TO DISPLAY");
    }

    @Test
    void frL8_frL9_theLastPageEndsTheBrowseAndAFurtherPf8SaysThereAreNoMorePages() {
        CardListResponse page = enter();
        for (int i = 0; i < 7; i++) {
            page = forward(page);
        }

        // 50 records: page 8 holds the single remaining card.
        assertThat(page.getPageNumber()).isEqualTo(8);
        assertThat(page.getCount()).isEqualTo(1);
        assertThat(page.getRows().get(0).getCardNum()).isEqualTo(LAST_CARD);
        assertThat(page.isNextPageExists()).isFalse();
        // ENDFILE during the read sets this; the "last page shown" latch is set now.
        assertThat(page.getErrorMessage()).isEqualTo("NO MORE RECORDS TO SHOW");
        assertThat(page.isLastPageShown()).isTrue();

        CardListResponse again = forward(page);
        assertThat(again.getPageNumber()).isEqualTo(8);
        assertThat(again.getErrorMessage()).isEqualTo("NO MORE PAGES TO DISPLAY");
    }

    @Test
    void frL3_theAccountFilterIsAppliedToEachRecordRead() {
        CardListResponse page = service.list("00000000050", null, "", null, null, null, null, null);

        assertThat(page.getCount()).isEqualTo(1);
        assertThat(page.getRows().get(0).getCardNum()).isEqualTo(PAGE1_FIRST);
        assertThat(page.getRows().get(0).getAcctId()).isEqualTo("00000000050");
    }

    @Test
    void frL4_theCardFilterSelectsTheSingleMatchingRecord() {
        CardListResponse page = service.list(null, "0923877193247330", "", null, null, null, null, null);

        assertThat(page.getCount()).isEqualTo(1);
        assertThat(page.getRows().get(0).getAcctId()).isEqualTo("00000000002");
    }

    @Test
    void frL12_theFiltersAreIndependentSoAMismatchedPairMatchesNothing() {
        // Card 0923877193247330 belongs to account 2, not 50: both tests must pass.
        CardListResponse page =
                service.list("00000000050", "0923877193247330", "", null, null, null, null, null);

        assertThat(page.getCount()).isZero();
        // WS-NO-RECORDS-FOUND never reaches the screen (quirk Q-11): the operator
        // sees the ENDFILE message on the error line and the usual info line.
        assertThat(page.getErrorMessage()).isEqualTo("NO MORE RECORDS TO SHOW");
        assertThat(page.getInfoMessage()).isEqualTo("TYPE S FOR DETAIL, U TO UPDATE ANY RECORD");
    }

    @Test
    void frL5_aBadFilterSuppressesTheBrowseEntirely() {
        assertThatThrownBy(() -> service.list("50", null, "", null, null, null, null, null))
                .isInstanceOf(CardValidationException.class)
                .hasMessage("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER");
    }
}
