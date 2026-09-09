package com.carddemo.authshell;

import com.carddemo.common.message.CardDemoMessages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * COMEN01C / COADM01C PROCESS-ENTER-KEY: option normalisation, the U/A access
 * rule, the COPAUS0C INQUIRE and the two "not installed" messages
 * (FR-MEN-06…12, FR-ADM-06…10).
 */
class MenuServiceTest {

    /** Every target defined, as the shipped CSD has them. */
    private final MenuService service = new MenuService(new ProgramInstallation(""));

    /** COPAUS0C absent from the estate, which is what the INQUIRE guards against. */
    private final MenuService withoutPendingAuth =
            new MenuService(new ProgramInstallation("COPAUS0C"));

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

    /** The typed field is echoed back into OPTIONO as two right-justified digits. */
    @Test
    void theScreenEchoesTheTwoDigitOption() {
        assertThat(service.selectMainMenuOption("6", "U").optionEcho()).isEqualTo("06");
        assertThat(service.selectMainMenuOption(" 6", "U").optionEcho()).isEqualTo("06");
        assertThat(service.selectMainMenuOption("6 ", "U").optionEcho()).isEqualTo("06");
        assertThat(service.selectMainMenuOption("06", "U").optionEcho()).isEqualTo("06");
        assertThat(service.selectMainMenuOption("11", "U").optionEcho()).isEqualTo("11");
    }

    @Test
    void rejectsZeroTooHighAndNonNumericOptions() {
        for (String typed : new String[] {null, "", "  ", "0", "00", "a", "1a", "12", "99"}) {
            MenuSelection selection = service.selectMainMenuOption(typed, "U");
            assertThat(selection.accepted())
                    .describedAs("option <%s>", typed).isFalse();
            assertThat(selection.option()).isNull();
            assertThat(selection.message()).isEqualTo(CardDemoMessages.MENU_INVALID_OPTION);
            assertThat(selection.messageColour()).isEqualTo(MessageColour.RED);
        }
    }

    @Test
    void rejectsAdminOptionsOutsideTheSixEntries() {
        assertThat(service.selectAdminMenuOption("7", "A").accepted()).isFalse();
        assertThat(service.selectAdminMenuOption("7", "A").message())
                .isEqualTo(CardDemoMessages.MENU_INVALID_OPTION);
        assertThat(service.selectAdminMenuOption("6", "A").accepted()).isTrue();
    }

    /**
     * No main-menu row carries USRTYPE 'A' in COMEN02Y, so the rule can only be
     * exercised against the table itself: an ordinary user reaches every row.
     */
    @Test
    void refusesAnAdminOnlyOptionToAUserTypeUser() {
        assertThat(MenuService.isAdminOnlyFor(new MenuOption(1, "Admin Thing", "COADMXXX", "A"),
                "U")).isTrue();
        assertThat(CardDemoMessages.MENU_ADMIN_ONLY).endsWith(" ");

        assertThat(service.mainMenu()).noneMatch(option -> "A".equals(option.userType()));
        for (int number = 1; number <= service.mainMenu().size(); number++) {
            assertThat(service.selectMainMenuOption(String.valueOf(number), "U").message())
                    .isNotEqualTo(CardDemoMessages.MENU_ADMIN_ONLY);
        }
    }

    /** The 'U' test is on the literal, so an unexpected type skips the check. */
    @Test
    void anUnexpectedUserTypeIsNotStoppedByTheAdminOnlyRule() {
        MenuOption adminOnly = new MenuOption(1, "Admin Thing", "COADMXXX", "A");
        assertThat(MenuService.isAdminOnlyFor(adminOnly, "A")).isFalse();
        assertThat(MenuService.isAdminOnlyFor(adminOnly, "X")).isFalse();
        assertThat(MenuService.isAdminOnlyFor(adminOnly, null)).isFalse();
    }

    @Test
    void copaus0cIsDispatchedOnlyWhenTheProgramIsInstalled() {
        MenuSelection selection = service.selectMainMenuOption("11", "U");
        assertThat(selection.accepted()).isTrue();
        assertThat(selection.option().programName()).isEqualTo("COPAUS0C");
    }

    /** EIBRESP not NORMAL: the option name is delimited by two spaces. */
    @Test
    void anUninstalledCopaus0cIsReportedInRed() {
        MenuSelection selection = withoutPendingAuth.selectMainMenuOption("11", "U");
        assertThat(selection.accepted()).isFalse();
        assertThat(selection.option().programName()).isEqualTo("COPAUS0C");
        assertThat(selection.message())
                .isEqualTo("This option Pending Authorization View is not installed...");
        assertThat(selection.messageColour()).isEqualTo(MessageColour.RED);
    }

    /** DELIMITED BY SPACE keeps only the first word, and no blank precedes "is". */
    @Test
    void aDummyProgramIsReportedAsComingSoon() {
        MenuOption dummy = new MenuOption(1, "Account View", "DUMMY001", "U");
        assertThat(CardDemoMessages.menuOptionComingSoon(dummy.paddedName()))
                .isEqualTo("This option Accountis coming soon ...");
    }

    /** COADM01C answers a missing target without naming the option. */
    @Test
    void anUninstalledAdminTargetIsReportedAsNotInstalled() {
        MenuService withoutUserList = new MenuService(new ProgramInstallation("COUSR00C"));
        MenuSelection selection = withoutUserList.selectAdminMenuOption("1", "A");
        assertThat(selection.accepted()).isFalse();
        assertThat(selection.option().programName()).isEqualTo("COUSR00C");
        assertThat(selection.message()).isEqualTo(CardDemoMessages.ADMIN_OPTION_NOT_INSTALLED);
        assertThat(selection.message()).doesNotContain("User List");
        assertThat(selection.messageColour()).isEqualTo(MessageColour.GREEN);
    }

    /** COADM01C has no user-type test: whoever reaches CA00 may select. */
    @Test
    void theAdminMenuHasNoUserTypeCheck() {
        assertThat(service.selectAdminMenuOption("1", "U").accepted()).isTrue();
        assertThat(service.selectAdminMenuOption("1", "A").accepted()).isTrue();
        assertThat(service.selectAdminMenuOption("1", null).accepted()).isTrue();
    }

    /**
     * A DUMMY target and a target that is not installed are indistinguishable on
     * CA00: both take the same green message, and neither names the option. No
     * shipped COADM02Y row points at a DUMMY program.
     */
    @Test
    void aDummyAdminProgramIsReportedAsNotInstalled() {
        assertThat(service.adminMenu())
                .noneMatch(option -> option.programName().startsWith(MenuCatalog.DUMMY_PREFIX));

        MenuService withoutUserAdd = new MenuService(new ProgramInstallation("COUSR01C"));
        MenuSelection selection = withoutUserAdd.selectAdminMenuOption("2", "A");
        assertThat(selection.message()).isEqualTo(CardDemoMessages.ADMIN_OPTION_NOT_INSTALLED);
        assertThat(selection.messageColour()).isEqualTo(MessageColour.GREEN);
    }

    /** COADM01C:121-129 is COMEN01C:117-125 verbatim. */
    @Test
    void adminOptionsUseTheSameParsingAsTheMainMenu() {
        assertThat(service.selectAdminMenuOption("1", "A").optionEcho()).isEqualTo("01");
        assertThat(service.selectAdminMenuOption(" 1", "A").optionEcho()).isEqualTo("01");
        assertThat(service.selectAdminMenuOption("1 ", "A").optionEcho()).isEqualTo("01");
        assertThat(service.selectAdminMenuOption("01", "A").option().programName())
                .isEqualTo("COUSR00C");
        assertThat(service.selectAdminMenuOption("a", "A").accepted()).isFalse();
    }

    @Test
    void spacesAreReplacedByZerosBeforeTheNumericTest() {
        assertThat(MenuService.normalisedOption("1")).isEqualTo("01");
        assertThat(MenuService.normalisedOption(" 1")).isEqualTo("01");
        assertThat(MenuService.normalisedOption("1 ")).isEqualTo("01");
        assertThat(MenuService.normalisedOption("")).isEqualTo("00");
        assertThat(MenuService.normalisedOption(null)).isEqualTo("00");
        assertThat(MenuService.normalisedOption("12")).isEqualTo("12");
    }
}
