package com.carddemo.authshell;

/**
 * One entry of {@code CDEMO-MENU-OPT} / {@code CDEMO-ADMIN-OPT}.
 *
 * @param number      CDEMO-MENU-OPT-NUM
 * @param name        CDEMO-MENU-OPT-NAME, verbatim from the copybook
 * @param programName CDEMO-MENU-OPT-PGMNAME, the COBOL program the option XCTLs to
 * @param userType    CDEMO-MENU-OPT-USRTYPE: 'U' for everyone, 'A' for administrators
 */
public record MenuOption(int number, String name, String programName, String userType) {
}
