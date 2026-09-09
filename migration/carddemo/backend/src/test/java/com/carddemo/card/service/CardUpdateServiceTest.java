package com.carddemo.card.service;

import com.carddemo.card.dto.CardUpdateRequest;
import com.carddemo.card.dto.CardUpdateResponse;
import com.carddemo.card.exception.CardRecordChangedException;
import com.carddemo.card.exception.CardUpdateFailedException;
import com.carddemo.card.exception.CardValidationException;
import com.carddemo.card.repository.CardBrowseRepository;
import com.carddemo.common.domain.CardRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-U1, FR-U8, FR-U10, FR-U14..FR-U17, FR-U19 coverage for {@link CardUpdateService} against the seeded H2
 * database. Traces to COCRDUPC's CCUP-CHANGE-ACTION state machine
 * (COCRDUPC.cbl:948-1031) and 9300-CHECK-CHANGE-IN-REC / the REWRITE
 * (COCRDUPC.cbl:1420-1539).
 *
 * <p>The tests mutate one card and put it back afterwards so the shared
 * in-memory H2 stays as the seed left it.
 */
@SpringBootTest
class CardUpdateServiceTest {

    /** carddata.txt: account 40, CVV 908, 'Davon Emmerich', expiry 2023-10-27. */
    private static final String CARD = "9805583408996588";
    private static final String ACCOUNT = "00000000040";

    @Autowired
    private CardUpdateService service;

    @Autowired
    private CardBrowseRepository repository;

    @AfterEach
    void restoreSeedRow() {
        CardRecord record = repository.findById(CARD).orElseThrow();
        record.setEmbossedName("Davon Emmerich");
        record.setActiveStatus("Y");
        record.setExpiraionDate("2023-10-27");
        repository.saveAndFlush(record);
    }

    private CardUpdateRequest request(String name, String status, String month, String year) {
        CardUpdateRequest request = new CardUpdateRequest();
        request.setAcctId(ACCOUNT);
        request.setCardNum(CARD);
        request.setNewName(name);
        request.setNewStatus(status);
        request.setNewExpiryMonth(month);
        request.setNewExpiryYear(year);
        request.setOldName("DAVON EMMERICH");
        request.setOldStatus("Y");
        request.setOldExpiryMonth("10");
        request.setOldExpiryYear("2023");
        request.setOldExpiryDay("27");
        request.setOldCvvCd(908);
        return request;
    }

    @Test
    void frU1_frU17_loadPresentsTheStoredDetails() {
        CardUpdateResponse loaded = service.load(ACCOUNT, CARD);

        assertThat(loaded.getState()).isEqualTo(CardUpdateResponse.State.SHOW_DETAILS);
        assertThat(loaded.getMessage()).isEqualTo("Details of selected card shown above");
        // INSPECT CONVERTING upper-cases the embossed name onto the screen.
        assertThat(loaded.getEmbossedName()).isEqualTo("DAVON EMMERICH");
        assertThat(loaded.getActiveStatus()).isEqualTo("Y");
        assertThat(loaded.getExpiryMonth()).isEqualTo("10");
        assertThat(loaded.getExpiryYear()).isEqualTo("2023");
        assertThat(loaded.getExpiryDay()).isEqualTo("27");
        assertThat(loaded.getCvvCd()).isEqualTo(908);
    }

    @Test
    void frU8_resubmittingTheFetchedValuesIsNoChange() {
        CardUpdateResponse response = service.update(request("Davon Emmerich", "y", "10", "2023"));

        // 1205-COMPARE-OLD-NEW compares the group case-insensitively.
        assertThat(response.getState()).isEqualTo(CardUpdateResponse.State.NO_CHANGES);
        assertThat(response.getMessage()).isEqualTo("No change detected with respect to values fetched.");
    }

    @Test
    void frU14_validEditsWaitForPf5() {
        CardUpdateResponse response = service.update(request("DAVON EMMERICHS", "N", "11", "2027"));

        assertThat(response.getState()).isEqualTo(CardUpdateResponse.State.CHANGES_OK_NOT_CONFIRMED);
        assertThat(response.getMessage()).isEqualTo("Changes validated.Press F5 to save");
        // Nothing is written before the confirmation.
        assertThat(repository.findById(CARD).orElseThrow().getEmbossedName()).isEqualTo("Davon Emmerich");
    }

    @Test
    void frU15_pf5RewritesTheRecordAndKeepsTheExpiryDayCvvAndAccount() {
        CardUpdateRequest request = request("DAVON EMMERICHS", "N", "11", "2027");
        request.setConfirmed(true);

        CardUpdateResponse response = service.update(request);

        assertThat(response.getState()).isEqualTo(CardUpdateResponse.State.CHANGES_OKAYED_AND_DONE);
        assertThat(response.getMessage()).isEqualTo("Changes committed to database");

        CardRecord stored = repository.findById(CARD).orElseThrow();
        assertThat(stored.getEmbossedName()).isEqualTo("DAVON EMMERICHS");
        assertThat(stored.getActiveStatus()).isEqualTo("N");
        // The day comes from the fetched record, not from the screen.
        assertThat(stored.getExpiraionDate()).isEqualTo("2027-11-27");
        assertThat(stored.getCvvCd()).isEqualTo(908);
        assertThat(stored.getAcctId()).isEqualTo(40L);
    }

    @Test
    void frU10_theFieldEditsRunBeforeTheConfirmation() {
        CardUpdateRequest request = request("DAVON 3MMERICH", "N", "11", "2027");
        request.setConfirmed(true);

        assertThatThrownBy(() -> service.update(request))
                .isInstanceOf(CardValidationException.class)
                .hasMessage("Card name can only contain alphabets and spaces");
        assertThat(repository.findById(CARD).orElseThrow().getActiveStatus()).isEqualTo("Y");
    }

    @Test
    void frU16_aRecordChangedSinceTheFetchIsRejectedAndRefreshed() {
        CardRecord meanwhile = repository.findById(CARD).orElseThrow();
        meanwhile.setActiveStatus("N");
        repository.saveAndFlush(meanwhile);

        CardUpdateRequest request = request("DAVON EMMERICHS", "Y", "11", "2027");
        request.setConfirmed(true);

        assertThatThrownBy(() -> service.update(request))
                .isInstanceOf(CardRecordChangedException.class)
                .hasMessage("Record changed by some one else. Please review");
        assertThat(repository.findById(CARD).orElseThrow().getEmbossedName()).isEqualTo("Davon Emmerich");
    }

    @Test
    void frU19_aRecordThatDisappearedCannotBeLocked() {
        CardUpdateRequest request = request("SOME NAME", "N", "11", "2027");
        request.setCardNum("9999999999999999");
        request.setConfirmed(true);

        assertThatThrownBy(() -> service.update(request))
                .isInstanceOf(CardUpdateFailedException.class)
                .hasMessage("Could not lock record for update");
    }
}
