package com.carddemo.transaction.service;

import com.carddemo.transaction.dto.TransactionListResponse;
import com.carddemo.transaction.dto.TransactionListRow;
import com.carddemo.transaction.exception.NonNumericTranIdException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-L1..FR-L4 / FR-L7 coverage for {@link TransactionListService} against the
 * H2 DB seeded by schema.sql + data.sql (30 transactions -> 3 pages of 10).
 * Traces to COTRN00C's PROCESS-ENTER-KEY / PROCESS-PAGE-FORWARD /
 * PROCESS-PAGE-BACKWARD browse behaviour.
 */
@SpringBootTest
class TransactionListServiceTest {

    @Autowired
    private TransactionListService service;

    private static String id(int n) {
        return String.format("%016d", n);
    }

    @Test
    void frL1_firstPage_returns10FromTheTopWithRowFormatting() {
        TransactionListResponse r = service.list(null, null);

        assertThat(r.getCount()).isEqualTo(10);
        assertThat(r.getRows()).hasSize(10);
        assertThat(r.getFirstId()).isEqualTo(id(1));
        assertThat(r.getLastId()).isEqualTo(id(10));
        assertThat(r.isHasPrevPage()).isFalse();
        assertThat(r.isHasNextPage()).isTrue();

        // Row 1: date sliced from TRAN-ORIG-TS -> MM/DD/YY, amount PIC +99999999.99.
        TransactionListRow first = r.getRows().get(0);
        assertThat(first.getId()).isEqualTo(id(1));
        assertThat(first.getDate()).isEqualTo("06/01/23");
        assertThat(first.getDescription()).isEqualTo("GROCERY PURCHASE");
        assertThat(first.getAmountDisplay()).isEqualTo("+00000013.75");

        // Row 4 is negative -> minus sign in the edited amount.
        TransactionListRow fourth = r.getRows().get(3);
        assertThat(fourth.getId()).isEqualTo(id(4));
        assertThat(fourth.getAmountDisplay()).isEqualTo("-00000025.00");
    }

    @Test
    void frL2_startIdFilter_beginsAtOrAfterThatTranId() {
        TransactionListResponse r = service.list(id(5), null);

        assertThat(r.getCount()).isEqualTo(10);
        assertThat(r.getFirstId()).isEqualTo(id(5));   // inclusive (STARTBR GTEQ)
        assertThat(r.getLastId()).isEqualTo(id(14));
        assertThat(r.isHasPrevPage()).isTrue();
        assertThat(r.isHasNextPage()).isTrue();
    }

    @Test
    void frL2_shortNumericFilter_isZeroPaddedToTheKeyWidth() {
        TransactionListResponse r = service.list("5", null);

        assertThat(r.getFirstId()).isEqualTo(id(5));
        assertThat(r.getLastId()).isEqualTo(id(14));
    }

    @Test
    void frL3_next_pagesForwardExclusiveOfLastId() {
        // Page 1 lastId = id(10); PF8 -> page 2 = ids 11..20.
        TransactionListResponse r = service.list(id(10), "next");

        assertThat(r.getCount()).isEqualTo(10);
        assertThat(r.getFirstId()).isEqualTo(id(11));
        assertThat(r.getLastId()).isEqualTo(id(20));
        assertThat(r.isHasPrevPage()).isTrue();
        assertThat(r.isHasNextPage()).isTrue();
    }

    @Test
    void frL3_next_lastPageHasNoNextPage() {
        // Page 2 lastId = id(20); PF8 -> page 3 = ids 21..30, at the bottom.
        TransactionListResponse r = service.list(id(20), "next");

        assertThat(r.getCount()).isEqualTo(10);
        assertThat(r.getFirstId()).isEqualTo(id(21));
        assertThat(r.getLastId()).isEqualTo(id(30));
        assertThat(r.isHasPrevPage()).isTrue();
        assertThat(r.isHasNextPage()).isFalse();
    }

    @Test
    void frL3_next_pastTheEndReturnsAnEmptyPage() {
        TransactionListResponse r = service.list(id(30), "next");

        assertThat(r.getCount()).isZero();
        assertThat(r.getRows()).isEmpty();
        assertThat(r.getFirstId()).isNull();
        assertThat(r.getLastId()).isNull();
        assertThat(r.isHasNextPage()).isFalse();
        assertThat(r.isHasPrevPage()).isFalse();
    }

    @Test
    void frL4_prev_pagesBackwardIntoAscendingOrder() {
        // On page 3 (firstId = id(21)); PF7 -> page 2 = ids 11..20 ascending.
        TransactionListResponse r = service.list(id(21), "prev");

        assertThat(r.getCount()).isEqualTo(10);
        assertThat(r.getFirstId()).isEqualTo(id(11));
        assertThat(r.getLastId()).isEqualTo(id(20));
        List<String> ids = r.getRows().stream().map(TransactionListRow::getId).toList();
        assertThat(ids).isSorted();
        assertThat(ids).containsExactly(
                id(11), id(12), id(13), id(14), id(15), id(16), id(17), id(18), id(19), id(20));
        assertThat(r.isHasPrevPage()).isTrue();
        assertThat(r.isHasNextPage()).isTrue();
    }

    @Test
    void frL4_prev_backToTheFirstPageHasNoPrevPage() {
        // On page 2 (firstId = id(11)); PF7 -> page 1 = ids 1..10, at the top.
        TransactionListResponse r = service.list(id(11), "prev");

        assertThat(r.getCount()).isEqualTo(10);
        assertThat(r.getFirstId()).isEqualTo(id(1));
        assertThat(r.getLastId()).isEqualTo(id(10));
        assertThat(r.isHasPrevPage()).isFalse();
        assertThat(r.isHasNextPage()).isTrue();
    }

    @Test
    void frL7_nonNumericFilter_throwsWithLegacyMessage() {
        assertThatThrownBy(() -> service.list("12AB", null))
                .isInstanceOf(NonNumericTranIdException.class)
                .hasMessage("Tran ID must be Numeric ...");
    }

    @Test
    void frL1_blankFilter_isTreatedAsFromTheTop() {
        TransactionListResponse r = service.list("   ", null);

        assertThat(r.getFirstId()).isEqualTo(id(1));
        assertThat(r.getLastId()).isEqualTo(id(10));
    }
}
