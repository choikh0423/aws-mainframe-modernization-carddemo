package com.carddemo.authshell;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-MEN-02/03 and FR-ADM-02/03: the tables as app/cpy/COMEN02Y.cpy and COADM02Y.cpy define them. */
class MenuCatalogTest {

    private static List<String> lines(List<MenuOption> options) {
        return options.stream()
                .map(o -> "%d|%s|%s|%s".formatted(o.number(), o.name(), o.programName(),
                        o.userType()))
                .toList();
    }

    @Test
    void mainMenuIsTheElevenRowsOfComen02y() {
        assertThat(lines(MenuCatalog.MAIN_MENU)).containsExactly(
                "1|Account View|COACTVWC|U",
                "2|Account Update|COACTUPC|U",
                "3|Credit Card List|COCRDLIC|U",
                "4|Credit Card View|COCRDSLC|U",
                "5|Credit Card Update|COCRDUPC|U",
                "6|Transaction List|COTRN00C|U",
                "7|Transaction View|COTRN01C|U",
                "8|Transaction Add|COTRN02C|U",
                "9|Transaction Reports|CORPT00C|U",
                "10|Bill Payment|COBIL00C|U",
                "11|Pending Authorization View|COPAUS0C|U");
    }

    /** COADM02Y has no CDEMO-ADMIN-OPT-USRTYPE column at all. */
    @Test
    void adminMenuIsTheSixRowsOfCoadm02y() {
        assertThat(lines(MenuCatalog.ADMIN_MENU)).containsExactly(
                "1|User List (Security)|COUSR00C|null",
                "2|User Add (Security)|COUSR01C|null",
                "3|User Update (Security)|COUSR02C|null",
                "4|User Delete (Security)|COUSR03C|null",
                "5|Transaction Type List/Update (Db2)|COTRTLIC|null",
                "6|Transaction Type Maintenance (Db2)|COTRTUPC|null");
    }

    /** STRING CDEMO-*-OPT-NUM '. ' CDEMO-*-OPT-NAME into the map's X(40) field. */
    @Test
    void optionLinesAreBuiltLikeBuildMenuOptions() {
        assertThat(MenuCatalog.MAIN_MENU.get(0).displayText())
                .hasSize(ScreenText.MENU_OPTION_LINE_LENGTH)
                .startsWith("01. Account View")
                .isEqualTo("01. Account View" + " ".repeat(24));
        assertThat(MenuCatalog.MAIN_MENU.get(9).displayText().stripTrailing())
                .isEqualTo("10. Bill Payment");
        assertThat(MenuCatalog.ADMIN_MENU.get(4).displayText().stripTrailing())
                .isEqualTo("05. Transaction Type List/Update (Db2)");

        assertThat(MenuCatalog.MAIN_MENU).allSatisfy(option -> {
            assertThat(option.paddedName()).hasSize(35);
            assertThat(option.displayText()).hasSize(ScreenText.MENU_OPTION_LINE_LENGTH);
        });
    }
}
