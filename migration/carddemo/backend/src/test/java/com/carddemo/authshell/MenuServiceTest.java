package com.carddemo.authshell;

import com.carddemo.common.message.CardDemoMessages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** COMEN01C / COADM01C option validation and the U/A access rule. */
class MenuServiceTest {

    private final MenuService service = new MenuService();

    @Test
    void mainMenuCarriesTheElevenCopybookOptions() {
        assertThat(service.mainMenu()).hasSize(11);
        assertThat(service.mainMenu().get(0).name()).isEqualTo("Account View");
        assertThat(service.mainMenu().get(10).programName()).isEqualTo("COPAUS0C");
    }

    @Test
    void adminMenuCarriesTheSixCopybookOptions() {
        assertThat(service.adminMenu()).hasSize(6);
        assertThat(service.adminMenu().get(4).name())
                .isEqualTo("Transaction Type List/Update (Db2)");
    }

    @Test
    void acceptsAValidOptionForAnOrdinaryUser() {
        MenuSelection selection = service.selectMainMenuOption("6", "U");
        assertThat(selection.accepted()).isTrue();
        assertThat(selection.option().programName()).isEqualTo("COTRN00C");
        assertThat(selection.message()).isEmpty();
    }

    @Test
    void spacesAreReplacedByZerosBeforeTheNumericTest() {
        assertThat(service.selectMainMenuOption(" 1", "U").accepted()).isTrue();
        assertThat(service.selectMainMenuOption("01", "U").accepted()).isTrue();
    }

    @Test
    void rejectsZeroTooHighAndNonNumericOptions() {
        for (String typed : new String[] {"0", "00", "12", "AB", "1A", "", "   ", "123"}) {
            MenuSelection selection = service.selectMainMenuOption(typed, "U");
            assertThat(selection.accepted()).as("option '%s'", typed).isFalse();
            assertThat(selection.message()).isEqualTo(CardDemoMessages.MENU_INVALID_OPTION);
        }
    }

    @Test
    void refusesAnAdminOptionToAnOrdinaryUser() {
        MenuSelection selection = service.selectAdminMenuOption("1", "U");
        assertThat(selection.accepted()).isFalse();
        assertThat(selection.message()).isEqualTo(CardDemoMessages.MENU_ADMIN_ONLY);
    }

    @Test
    void allowsAnAdminOptionToAnAdministrator() {
        MenuSelection selection = service.selectAdminMenuOption("1", "A");
        assertThat(selection.accepted()).isTrue();
        assertThat(selection.option().programName()).isEqualTo("COUSR00C");
    }
}
