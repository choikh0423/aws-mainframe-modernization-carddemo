package com.carddemo.trantype.service;

import com.carddemo.trantype.dto.TranTypeListDisplayRow;
import com.carddemo.trantype.dto.TranTypeListRequest;
import com.carddemo.trantype.dto.TranTypeListResponse;
import com.carddemo.trantype.dto.TranTypeListRowInput;
import com.carddemo.trantype.dto.TranTypeListState;
import com.carddemo.trantype.message.TranTypeMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CTLI ({@code COTRTLIC.cbl}) driven one pseudo-conversation at a time, against
 * its own in-memory database so the update and delete paths do not disturb the
 * seeded database the other tests share.
 *
 * <p>Nine transaction types make two pages of the seven-line map; only '01'
 * carries child categories, so it is the row whose delete is refused.
 *
 * <p>Covers FR-L01..FR-L03, FR-L05..FR-L21 and FR-L25..FR-L27.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-trantype-list;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class TranTypeListServiceTest {

    @Autowired
    private TranTypeListService service;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("delete from db2_transaction_type_category");
        jdbcTemplate.update("delete from db2_transaction_type");
        for (int i = 1; i <= 9; i++) {
            jdbcTemplate.update("insert into db2_transaction_type (tr_type, tr_description) values (?, ?)",
                    String.format("%02d", i), "Type " + i);
        }
        jdbcTemplate.update("insert into db2_transaction_type_category"
                + " (trc_type_code, trc_type_category, trc_cat_data) values ('01', '0001', 'Child')");
    }

    @Test
    void firstEntryShowsTheFirstSevenTypesAndTheActionPrompt() {
        TranTypeListResponse response = enter(null);

        assertThat(response.rows()).extracting(TranTypeListDisplayRow::typeCode)
                .containsExactly("01", "02", "03", "04", "05", "06", "07");
        assertThat(response.rows().get(0).description()).isEqualTo("Type 1");
        assertThat(response.pageNumber()).isEqualTo(1);
        assertThat(response.infoMessage()).isEqualTo(TranTypeMessages.LIST_INFO_REC_ACTIONS);
        assertThat(response.errorMessage()).isEmpty();
        assertThat(response.state().isNextPageExists()).isTrue();
    }

    @Test
    void pageDownShowsTheRemainingTypesAndPageUpComesBack() {
        TranTypeListResponse page1 = enter(null);

        TranTypeListResponse page2 = service.handle(request("PF8", "", "", blankRows(), page1.state()));
        assertThat(page2.rows()).extracting(TranTypeListDisplayRow::typeCode)
                .containsExactly("08", "09", "", "", "", "", "");
        assertThat(page2.pageNumber()).isEqualTo(2);

        TranTypeListResponse back = service.handle(request("PF7", "", "", blankRows(), page2.state()));
        assertThat(back.rows()).extracting(TranTypeListDisplayRow::typeCode)
                .containsExactly("01", "02", "03", "04", "05", "06", "07");
        assertThat(back.pageNumber()).isEqualTo(1);
    }

    @Test
    void pageUpOnTheFirstPageSaysThereAreNoPreviousPages() {
        TranTypeListResponse page1 = enter(null);

        TranTypeListResponse response = service.handle(request("PF7", "", "", blankRows(), page1.state()));

        assertThat(response.errorMessage()).isEqualTo(TranTypeMessages.LIST_NO_PREVIOUS_PAGES);
        assertThat(response.rows()).extracting(TranTypeListDisplayRow::typeCode)
                .startsWith("01", "02");
    }

    @Test
    void pageDownPastTheEndSaysSoAndThenRefusesToMove() {
        TranTypeListResponse page2 =
                service.handle(request("PF8", "", "", blankRows(), enter(null).state()));

        // 8000-READ-FORWARD ran out of rows while filling the page.
        assertThat(page2.errorMessage()).isEqualTo(TranTypeMessages.LIST_NO_MORE_RECORDS);
        assertThat(page2.state().isLastPageShown()).isTrue();

        TranTypeListResponse again = service.handle(request("PF8", "", "", blankRows(), page2.state()));

        assertThat(again.errorMessage()).isEqualTo(TranTypeMessages.LIST_NO_MORE_PAGES);
        assertThat(again.rows()).extracting(TranTypeListDisplayRow::typeCode).startsWith("08", "09");
    }

    @Test
    void aNonNumericTypeFilterIsRejected() {
        TranTypeListResponse response =
                service.handle(request("ENTER", "A1", "", blankRows(), enter(null).state()));

        assertThat(response.errorMessage()).isEqualTo(TranTypeMessages.LIST_TYPE_FILTER_INVALID);
        assertThat(response.protectSelectRows()).isTrue();
    }

    @Test
    void aTypeFilterThatMatchesNothingIsRejected() {
        TranTypeListResponse response =
                service.handle(request("ENTER", "99", "", blankRows(), enter(null).state()));

        assertThat(response.errorMessage()).isEqualTo(TranTypeMessages.LIST_NO_RECORDS_FOR_FILTERS);
    }

    @Test
    void aTypeFilterNarrowsTheBrowseToOneRow() {
        TranTypeListResponse response =
                service.handle(request("ENTER", "03", "", blankRows(), enter(null).state()));

        assertThat(response.errorMessage()).isEmpty();
        assertThat(response.rows()).extracting(TranTypeListDisplayRow::typeCode)
                .containsExactly("03", "", "", "", "", "", "");
    }

    @Test
    void aDescriptionFilterMatchesOnAContainedString() {
        TranTypeListResponse response =
                service.handle(request("ENTER", "", "Type 9", blankRows(), enter(null).state()));

        assertThat(response.rows()).extracting(TranTypeListDisplayRow::typeCode)
                .containsExactly("09", "", "", "", "", "", "");
    }

    @Test
    void twoSelectionsAreRefused() {
        List<TranTypeListRowInput> rows = blankRows();
        rows.set(0, new TranTypeListRowInput("D", "Type 1"));
        rows.set(1, new TranTypeListRowInput("U", "Type 2"));

        TranTypeListResponse response = service.handle(request("ENTER", "", "", rows, enter(null).state()));

        assertThat(response.errorMessage()).isEqualTo(TranTypeMessages.LIST_MORE_THAN_1_ACTION);
        assertThat(response.rows().get(0).inError()).isTrue();
        assertThat(response.rows().get(1).inError()).isTrue();
    }

    @Test
    void anActionCodeOtherThanUpperCaseUOrDIsRefused() {
        List<TranTypeListRowInput> rows = blankRows();
        rows.set(2, new TranTypeListRowInput("X", "Type 3"));

        TranTypeListResponse response = service.handle(request("ENTER", "", "", rows, enter(null).state()));

        assertThat(response.errorMessage()).isEqualTo(TranTypeMessages.LIST_INVALID_ACTION_CODE);
        assertThat(response.rows().get(2).inError()).isTrue();
    }

    @Test
    void selectingUWithoutChangingTheDescriptionReportsNoChange() {
        List<TranTypeListRowInput> rows = blankRows();
        rows.set(0, new TranTypeListRowInput("U", "Type 1"));

        TranTypeListResponse response = service.handle(request("ENTER", "", "", rows, enter(null).state()));

        assertThat(response.infoMessage()).isEqualTo(TranTypeMessages.LIST_NO_CHANGES_DETECTED);
    }

    @Test
    void selectingUWithAChangePromptsForF10AndTheSaveUpdatesTheRow() {
        List<TranTypeListRowInput> rows = blankRows();
        rows.set(1, new TranTypeListRowInput("U", "Type 2 amended"));

        TranTypeListResponse prompt = service.handle(request("ENTER", "", "", rows, enter(null).state()));
        assertThat(prompt.infoMessage()).isEqualTo(TranTypeMessages.LIST_INFO_UPDATE);
        assertThat(prompt.rows().get(1).highlighted()).isTrue();
        assertThat(prompt.state().isUpdateRequested()).isTrue();

        TranTypeListResponse saved = service.handle(request("PF10", "", "", rows, prompt.state()));

        assertThat(saved.infoMessage()).isEqualTo(TranTypeMessages.LIST_INFO_UPDATE_SUCCESS);
        assertThat(description("02")).isEqualTo("Type 2 amended");
    }

    @Test
    void selectingUWithAnInvalidDescriptionIsRefused() {
        List<TranTypeListRowInput> rows = blankRows();
        rows.set(1, new TranTypeListRowInput("U", "Type-2"));

        TranTypeListResponse response = service.handle(request("ENTER", "", "", rows, enter(null).state()));

        assertThat(response.errorMessage()).isEqualTo(TranTypeMessages.DESCRIPTION_NOT_ALPHANUM);
        assertThat(description("02")).isEqualTo("Type 2");
    }

    @Test
    void selectingDPromptsForF10AndTheConfirmationDeletesTheRow() {
        TranTypeListResponse page2 =
                service.handle(request("PF8", "", "", blankRows(), enter(null).state()));
        List<TranTypeListRowInput> rows = blankRows();
        rows.set(1, new TranTypeListRowInput("D", "Type 9"));

        TranTypeListResponse prompt = service.handle(request("ENTER", "", "", rows, page2.state()));
        assertThat(prompt.infoMessage()).isEqualTo(TranTypeMessages.LIST_INFO_DELETE);
        assertThat(prompt.rows().get(1).highlighted()).isTrue();

        TranTypeListResponse deleted = service.handle(request("PF10", "", "", rows, prompt.state()));

        assertThat(deleted.infoMessage()).isEqualTo(TranTypeMessages.LIST_INFO_DELETE_SUCCESS);
        assertThat(count("09")).isZero();
        assertThat(deleted.state().getScreenNum()).isEqualTo(1);
    }

    @Test
    void deletingATypeThatStillHasCategoriesIsRefused() {
        List<TranTypeListRowInput> rows = blankRows();
        rows.set(0, new TranTypeListRowInput("D", "Type 1"));

        TranTypeListResponse prompt = service.handle(request("ENTER", "", "", rows, enter(null).state()));
        TranTypeListResponse refused = service.handle(request("PF10", "", "", rows, prompt.state()));

        assertThat(refused.errorMessage()).isEqualTo(
                TranTypeMessages.withSqlCode(TranTypeMessages.LIST_DELETE_HAS_CHILDREN, -532));
        assertThat(count("01")).isEqualTo(1);
    }

    @Test
    void updatingARowSomeoneElseDeletedReportsIt() {
        List<TranTypeListRowInput> rows = blankRows();
        rows.set(1, new TranTypeListRowInput("U", "Type 2 amended"));
        TranTypeListResponse prompt = service.handle(request("ENTER", "", "", rows, enter(null).state()));

        jdbcTemplate.update("delete from db2_transaction_type where tr_type = '02'");
        TranTypeListResponse refused = service.handle(request("PF10", "", "", rows, prompt.state()));

        assertThat(refused.errorMessage()).isEqualTo(
                TranTypeMessages.withSqlCode(TranTypeMessages.LIST_UPDATE_NOT_FOUND, 100));
        assertThat(refused.state().isUpdateRequested()).isTrue();
    }

    @Test
    void f3ReturnsToTheAdminMenuAndF2GoesToTheUpdateScreen() {
        TranTypeListResponse first = enter(null);

        TranTypeListResponse exit = service.handle(request("PF3", "", "", blankRows(), first.state()));
        assertThat(exit.nextProgram()).isEqualTo("COADM01C");
        assertThat(exit.nextTranId()).isEqualTo("CA00");

        TranTypeListResponse add = service.handle(request("PF2", "", "", blankRows(), first.state()));
        assertThat(add.nextProgram()).isEqualTo("COTRTUPC");
        assertThat(add.nextTranId()).isEqualTo("CTTU");
    }

    @Test
    void anUnmappedKeyIsTreatedAsEnter() {
        TranTypeListResponse response =
                service.handle(request("PF9", "", "", blankRows(), enter(null).state()));

        assertThat(response.nextProgram()).isEqualTo("COTRTLIC");
        assertThat(response.infoMessage()).isEqualTo(TranTypeMessages.LIST_INFO_REC_ACTIONS);
        assertThat(response.rows()).extracting(TranTypeListDisplayRow::typeCode).startsWith("01");
    }

    @Test
    void anEmptyTableReportsThatNoRecordsWereFound() {
        jdbcTemplate.update("delete from db2_transaction_type_category");
        jdbcTemplate.update("delete from db2_transaction_type");

        TranTypeListResponse response = enter(null);

        assertThat(response.errorMessage()).isEqualTo(TranTypeMessages.LIST_NO_RECORDS_FOUND);
        assertThat(response.infoMessage()).isEmpty();
    }

    private TranTypeListResponse enter(TranTypeListState state) {
        return service.handle(request("ENTER", "", "", blankRows(), state));
    }

    private static TranTypeListRequest request(String aid, String typeFilter, String descFilter,
                                               List<TranTypeListRowInput> rows,
                                               TranTypeListState state) {
        return new TranTypeListRequest(aid, typeFilter, descFilter, rows, state);
    }

    private static List<TranTypeListRowInput> blankRows() {
        List<TranTypeListRowInput> rows = new ArrayList<>();
        for (int i = 0; i < TranTypeListService.MAX_SCREEN_LINES; i++) {
            rows.add(TranTypeListRowInput.blank());
        }
        return rows;
    }

    private String description(String typeCode) {
        return jdbcTemplate.queryForObject(
                "select tr_description from db2_transaction_type where tr_type = ?",
                String.class, typeCode);
    }

    private int count(String typeCode) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from db2_transaction_type where tr_type = ?", Integer.class, typeCode);
        return count == null ? 0 : count;
    }
}
