package com.carddemo.pendingauth.service;

import com.carddemo.pendingauth.dto.PendingAuthListResponse;
import com.carddemo.pendingauth.dto.PendingAuthListRow;
import com.carddemo.pendingauth.dto.PendingAuthSelectionRequest;
import com.carddemo.pendingauth.dto.PendingAuthSelectionResponse;
import com.carddemo.pendingauth.service.PendingAuthListService.Direction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CPVS / COPAUS0C: FR-S1..FR-S9 against the seeded DBPAUTP0 demo hierarchy
 * (account 1 has seven children, account 2 one).
 *
 * <p>Read-only, so it shares the cached default {@code @SpringBootTest} context
 * with the rest of the suite.
 */
@SpringBootTest
class PendingAuthListServiceTest {

    @Autowired
    private PendingAuthListService service;

    @Test
    void frS1_blankAccountIdIsRejectedWithTheLegacyMessage() {
        assertThat(service.list("   ", Direction.FIRST, null, 0).message())
                .isEqualTo("Please enter Acct Id...");
        assertThat(service.list(null, Direction.FIRST, null, 0).message())
                .isEqualTo("Please enter Acct Id...");
    }

    @Test
    void frS2_nonNumericAccountIdIsRejectedWithTheLegacyMessage() {
        assertThat(service.list("00000ABC001", Direction.FIRST, null, 0).message())
                .isEqualTo("Acct Id must be Numeric ...");
    }

    @Test
    void frS3_anAccountAbsentFromTheXrefReportsTheCicsResponse() {
        assertThat(service.list("99999999999", Direction.FIRST, null, 0).message())
                .isEqualTo("Account:99999999999 not found in XREF file."
                        + " Resp:000000013 Reas:000000000");
    }

    @Test
    void frS4_headerCarriesTheAccountCustomerAndSummaryFields() {
        PendingAuthListResponse response = service.list("1", Direction.FIRST, null, 0);

        assertThat(response.acctId()).isEqualTo("00000000001");
        assertThat(response.custId()).isEqualTo("000000001");
        assertThat(response.customerName()).isEqualTo("Immanuel M Kessler");
        assertThat(response.addressLine1()).isEqualTo("618 Deshaun Route,Apt. 802");
        assertThat(response.addressLine2()).isEqualTo("Altenwerthshire,NC,12546");
        assertThat(response.creditLimit()).isEqualTo("     2020.00");
        assertThat(response.cashLimit()).isEqualTo("  1020.00");
        assertThat(response.creditBalance()).isEqualTo("      262.55");
        assertThat(response.approvedCount()).isEqualTo("006");
        assertThat(response.declinedCount()).isEqualTo("001");
        assertThat(response.approvedAmount()).isEqualTo("   262.55");
        assertThat(response.declinedAmount()).isEqualTo("   500.00");
        assertThat(response.message()).isNull();
    }

    @Test
    void frS5_firstPageShowsFiveRowsInSegmentOrderAndFlagsTheSixthRead() {
        PendingAuthListResponse response = service.list("1", Direction.FIRST, null, 0);

        assertThat(response.rows()).hasSize(5);
        assertThat(response.rows()).extracting(PendingAuthListRow::transactionId)
                .containsExactly("PAUTH0000000001", "PAUTH0000000002", "PAUTH0000000003",
                        "PAUTH0000000004", "PAUTH0000000005");
        assertThat(response.nextPage()).isTrue();
        assertThat(response.pageNum()).isEqualTo(1);

        PendingAuthListRow first = response.rows().get(0);
        assertThat(first.authKey()).isEqualTo("74984849999999");
        assertThat(first.date()).isEqualTo("01/15/25");
        assertThat(first.time()).isEqualTo("15:00:00");
        assertThat(first.authType()).isEqualTo("0100");
        assertThat(first.approvalStatus()).isEqualTo("A");
        assertThat(first.matchStatus()).isEqualTo("P");
        // PAMTnnn carries PA-APPROVED-AMT, not the transaction amount (quirk Q-1).
        assertThat(first.amountDisplay()).isEqualTo("       13.75");
        assertThat(response.rows().get(2).approvalStatus()).isEqualTo("D");
        assertThat(response.rows().get(2).amountDisplay()).isEqualTo("        0.00");
    }

    @Test
    void frS6_pf8PagesForwardFromTheLastKeyAndTheLastPageHasNoNextFlag() {
        PendingAuthListResponse first = service.list("1", Direction.FIRST, null, 0);
        PendingAuthListResponse second =
                service.list("1", Direction.NEXT, first.lastKey(), first.pageNum());

        assertThat(second.rows()).extracting(PendingAuthListRow::transactionId)
                .containsExactly("PAUTH0000000006", "PAUTH0000000007");
        assertThat(second.nextPage()).isFalse();
        assertThat(second.pageNum()).isEqualTo(2);

        PendingAuthListResponse past =
                service.list("1", Direction.NEXT, second.lastKey(), second.pageNum());
        assertThat(past.rows()).isEmpty();
        assertThat(past.message()).isEqualTo("You are already at the bottom of the page...");
    }

    @Test
    void frS7_pf7PagesBackToThePageStartingAtTheGivenKey() {
        PendingAuthListResponse first = service.list("1", Direction.FIRST, null, 0);
        PendingAuthListResponse second =
                service.list("1", Direction.NEXT, first.lastKey(), first.pageNum());
        PendingAuthListResponse back =
                service.list("1", Direction.PREVIOUS, first.firstKey(), second.pageNum());

        assertThat(back.rows()).extracting(PendingAuthListRow::transactionId)
                .containsExactly("PAUTH0000000001", "PAUTH0000000002", "PAUTH0000000003",
                        "PAUTH0000000004", "PAUTH0000000005");
        assertThat(back.pageNum()).isEqualTo(1);

        assertThat(service.list("1", Direction.PREVIOUS, null, 1).message())
                .isEqualTo("You are already at the top of the page...");
    }

    @Test
    void frS8_anAccountWithNoSummarySegmentShowsZeroesAndNoRows() {
        PendingAuthListResponse response = service.list("3", Direction.FIRST, null, 0);

        assertThat(response.rows()).isEmpty();
        assertThat(response.approvedCount()).isEqualTo("000");
        assertThat(response.declinedCount()).isEqualTo("000");
        assertThat(response.creditBalance()).isEqualTo("        0.00");
        assertThat(response.message()).isNull();
    }

    @Test
    void frS9_theFirstNonBlankSelectionWinsAndOnlySTransfersToCpvd() {
        PendingAuthSelectionResponse selected = service.select(new PendingAuthSelectionRequest("1",
                List.of(new PendingAuthSelectionRequest.Row(" ", "74984849999999"),
                        new PendingAuthSelectionRequest.Row("s", "74984859999999"),
                        new PendingAuthSelectionRequest.Row("S", "74984869999999"))));
        assertThat(selected.selected()).isTrue();
        assertThat(selected.nextProgram()).isEqualTo("COPAUS1C");
        assertThat(selected.nextTranId()).isEqualTo("CPVD");
        assertThat(selected.authKey()).isEqualTo("74984859999999");

        PendingAuthSelectionResponse invalid = service.select(new PendingAuthSelectionRequest("1",
                List.of(new PendingAuthSelectionRequest.Row("X", "74984849999999"),
                        new PendingAuthSelectionRequest.Row("S", "74984859999999"))));
        assertThat(invalid.selected()).isFalse();
        assertThat(invalid.message()).isEqualTo("Invalid selection. Valid value is S");

        PendingAuthSelectionResponse none = service.select(new PendingAuthSelectionRequest("1",
                List.of(new PendingAuthSelectionRequest.Row("", "74984849999999"))));
        assertThat(none.selected()).isFalse();
        assertThat(none.message()).isNull();

        assertThat(service.select(new PendingAuthSelectionRequest("  ", List.of())).message())
                .isEqualTo("Please enter Acct Id...");
    }
}
