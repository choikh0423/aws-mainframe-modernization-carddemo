package com.carddemo.trantype.service;

import com.carddemo.trantype.dto.TranTypeUpdateRequest;
import com.carddemo.trantype.dto.TranTypeUpdateResponse;
import com.carddemo.trantype.dto.TranTypeUpdateState;
import com.carddemo.trantype.message.TranTypeMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CTTU ({@code COTRTUPC.cbl}) as the state machine its {@code TTUP-CHANGE-ACTION}
 * describes, against its own in-memory database.
 *
 * <p>'01' keeps a child category so its delete is refused; '09' has none.
 *
 * <p>Covers FR-U01..FR-U03, FR-U05..FR-U22 and FR-U24.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-trantype-update;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class TranTypeUpdateServiceTest {

    @Autowired
    private TranTypeUpdateService service;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("delete from db2_transaction_type_category");
        jdbcTemplate.update("delete from db2_transaction_type");
        jdbcTemplate.update("insert into db2_transaction_type (tr_type, tr_description)"
                + " values ('01', 'Purchase')");
        jdbcTemplate.update("insert into db2_transaction_type (tr_type, tr_description)"
                + " values ('07', 'Adjustment')");
        jdbcTemplate.update("insert into db2_transaction_type (tr_type, tr_description)"
                + " values ('09', 'Spare')");
        jdbcTemplate.update("insert into db2_transaction_type_category"
                + " (trc_type_code, trc_type_category, trc_cat_data) values ('01', '0001', 'Child')");
    }

    @Test
    void firstEntryAsksForTheKeyOnAnEmptyScreen() {
        TranTypeUpdateResponse response = firstEntry();

        assertThat(response.typeCode()).isEmpty();
        assertThat(response.description()).isEmpty();
        assertThat(response.infoMessage()).isEqualTo(TranTypeMessages.UPD_INFO_ENTER_KEY);
        assertThat(response.errorMessage()).isEmpty();
        assertThat(response.typeCodeEditable()).isTrue();
        assertThat(response.descriptionEditable()).isFalse();
    }

    @Test
    void lookingUpAnExistingTypeShowsItsDetails() {
        TranTypeUpdateResponse response = lookup("01");

        assertThat(response.typeCode()).isEqualTo("01");
        assertThat(response.description()).isEqualTo("Purchase");
        assertThat(response.state().getChangeAction()).isEqualTo(TranTypeUpdateState.SHOW_DETAILS);
        assertThat(response.infoMessage()).isEqualTo(TranTypeMessages.UPD_INFO_ENTER_KEY);
        assertThat(response.f4Enabled()).isTrue();
        assertThat(response.descriptionEditable()).isTrue();
        assertThat(response.typeCodeEditable()).isFalse();
    }

    @Test
    void aSingleDigitKeyIsPaddedToTwoDigits() {
        TranTypeUpdateResponse response = lookup("7");

        assertThat(response.typeCode()).isEqualTo("07");
        assertThat(response.description()).isEqualTo("Adjustment");
    }

    @Test
    void aMissingKeyIsRefusedAndComesBackAsAStar() {
        TranTypeUpdateResponse response = lookup("");

        assertThat(response.errorMessage()).isEqualTo(TranTypeMessages.TYPE_CODE_REQUIRED);
        assertThat(response.typeCode()).isEqualTo("*");
    }

    @Test
    void aNonNumericKeyIsRefused() {
        assertThat(lookup("AB").errorMessage()).isEqualTo(TranTypeMessages.TYPE_CODE_NOT_NUMERIC);
    }

    @Test
    void aZeroKeyIsRefused() {
        assertThat(lookup("00").errorMessage()).isEqualTo(TranTypeMessages.TYPE_CODE_ZERO);
    }

    @Test
    void aKeyThatIsNotInTheTableOffersTheAddPath() {
        TranTypeUpdateResponse response = lookup("42");

        assertThat(response.errorMessage()).isEqualTo(TranTypeMessages.UPD_NOT_FOUND);
        assertThat(response.infoMessage()).isEqualTo(TranTypeMessages.UPD_INFO_PRESS_F5_TO_ADD);
        assertThat(response.state().getChangeAction()).isEqualTo(TranTypeUpdateState.DETAILS_NOT_FOUND);
        assertThat(response.f5Enabled()).isTrue();
    }

    @Test
    void resendingTheSameDetailsReportsNoChange() {
        TranTypeUpdateResponse shown = lookup("01");

        TranTypeUpdateResponse response = send("ENTER", "01", "Purchase", shown.state());

        assertThat(response.errorMessage()).isEqualTo(TranTypeMessages.UPD_NO_CHANGE_DETECTED);
        assertThat(response.state().getChangeAction()).isEqualTo(TranTypeUpdateState.SHOW_DETAILS);
    }

    @Test
    void anInvalidDescriptionIsRefused() {
        TranTypeUpdateResponse shown = lookup("01");

        TranTypeUpdateResponse response = send("ENTER", "01", "Purchase-2", shown.state());

        assertThat(response.errorMessage()).isEqualTo(TranTypeMessages.DESCRIPTION_NOT_ALPHANUM);
        assertThat(response.state().getChangeAction()).isEqualTo(TranTypeUpdateState.CHANGES_NOT_OK);
        assertThat(response.infoMessage()).isEqualTo(TranTypeMessages.UPD_INFO_UPDATE_DETAILS);
    }

    @Test
    void aChangedDescriptionIsValidatedAndThenCommittedByF5() {
        TranTypeUpdateResponse shown = lookup("01");

        TranTypeUpdateResponse validated = send("ENTER", "01", "Purchase two", shown.state());
        assertThat(validated.infoMessage()).isEqualTo(TranTypeMessages.UPD_INFO_CHANGES_VALIDATED);
        assertThat(validated.state().getChangeAction())
                .isEqualTo(TranTypeUpdateState.CHANGES_OK_NOT_CONFIRMED);
        assertThat(validated.f5Enabled()).isTrue();

        TranTypeUpdateResponse committed = send("PF5", "01", "Purchase two", validated.state());

        assertThat(committed.infoMessage()).isEqualTo(TranTypeMessages.UPD_INFO_CHANGES_COMMITTED);
        assertThat(description("01")).isEqualTo("Purchase two");
    }

    @Test
    void f12BacksOutAValidatedChange() {
        TranTypeUpdateResponse validated = send("ENTER", "01", "Purchase two", lookup("01").state());

        TranTypeUpdateResponse cancelled = send("PF12", "01", "Purchase two", validated.state());

        assertThat(cancelled.errorMessage()).isEqualTo(TranTypeMessages.UPD_UPDATE_CANCELLED);
        assertThat(description("01")).isEqualTo("Purchase");
    }

    @Test
    void f5OnAMissingKeyOpensTheAddScreenAndSavesTheNewRecord() {
        TranTypeUpdateResponse notFound = lookup("42");

        TranTypeUpdateResponse blank = send("PF5", "42", "", notFound.state());
        assertThat(blank.infoMessage()).isEqualTo(TranTypeMessages.UPD_INFO_ENTER_NEW_DETAILS);
        assertThat(blank.state().getChangeAction()).isEqualTo(TranTypeUpdateState.CREATE_NEW_RECORD);

        TranTypeUpdateResponse validated = send("ENTER", "42", "Late fee", blank.state());
        assertThat(validated.infoMessage()).isEqualTo(TranTypeMessages.UPD_INFO_CHANGES_VALIDATED);

        TranTypeUpdateResponse committed = send("PF5", "42", "Late fee", validated.state());

        assertThat(committed.infoMessage()).isEqualTo(TranTypeMessages.UPD_INFO_CHANGES_COMMITTED);
        assertThat(description("42")).isEqualTo("Late fee");
    }

    @Test
    void f4TwiceDeletesARecordWithNoCategories() {
        TranTypeUpdateResponse shown = lookup("09");

        TranTypeUpdateResponse confirm = send("PF4", "09", "Spare", shown.state());
        assertThat(confirm.infoMessage()).isEqualTo(TranTypeMessages.UPD_INFO_CONFIRM_DELETE);
        assertThat(confirm.state().getChangeAction()).isEqualTo(TranTypeUpdateState.CONFIRM_DELETE);

        TranTypeUpdateResponse deleted = send("PF4", "09", "Spare", confirm.state());

        assertThat(deleted.infoMessage()).isEqualTo(TranTypeMessages.UPD_INFO_DELETE_SUCCESS);
        assertThat(count("09")).isZero();
    }

    @Test
    void deletingATypeThatStillHasCategoriesIsRefused() {
        TranTypeUpdateResponse confirm = send("PF4", "01", "Purchase", lookup("01").state());

        TranTypeUpdateResponse refused = send("PF4", "01", "Purchase", confirm.state());

        assertThat(refused.errorMessage()).isEqualTo(
                TranTypeMessages.withSqlCode(TranTypeMessages.UPD_DELETE_HAS_CHILDREN, -532));
        assertThat(count("01")).isEqualTo(1);
    }

    @Test
    void f12CancelsAPendingDelete() {
        TranTypeUpdateResponse confirm = send("PF4", "09", "Spare", lookup("09").state());

        TranTypeUpdateResponse cancelled = send("PF12", "09", "Spare", confirm.state());

        assertThat(cancelled.errorMessage()).isEqualTo(TranTypeMessages.UPD_DELETE_CANCELLED);
        assertThat(count("09")).isEqualTo(1);
    }

    @Test
    void aKeyTheCurrentStateDoesNotAllowIsRefused() {
        TranTypeUpdateResponse start = firstEntry();

        TranTypeUpdateResponse response = send("PF4", "", "", start.state());

        assertThat(response.errorMessage()).isEqualTo(TranTypeMessages.UPD_INVALID_KEY);
    }

    @Test
    void f3LeavesForTheAdminMenu() {
        TranTypeUpdateResponse response = send("PF3", "", "", firstEntry().state());

        assertThat(response.nextProgram()).isEqualTo("COADM01C");
        assertThat(response.nextTranId()).isEqualTo("CA00");
    }

    private TranTypeUpdateResponse firstEntry() {
        return service.handle(new TranTypeUpdateRequest("ENTER", "", "", null));
    }

    private TranTypeUpdateResponse lookup(String typeCode) {
        return send("ENTER", typeCode, "", firstEntry().state());
    }

    private TranTypeUpdateResponse send(String aid, String typeCode, String description,
                                        TranTypeUpdateState state) {
        return service.handle(new TranTypeUpdateRequest(aid, typeCode, description, state));
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
