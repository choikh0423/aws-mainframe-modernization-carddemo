package com.carddemo.authshell;

/**
 * One entry of {@code CDEMO-MENU-OPT} (app/cpy/COMEN02Y.cpy:94-98) or
 * {@code CDEMO-ADMIN-OPT} (app/cpy/COADM02Y.cpy:56-59).
 *
 * @param number      CDEMO-*-OPT-NUM, PIC 9(02)
 * @param name        CDEMO-*-OPT-NAME, the copybook literal without its X(35) padding
 * @param programName CDEMO-*-OPT-PGMNAME, the program the option XCTLs to
 * @param userType    CDEMO-MENU-OPT-USRTYPE; null for admin options, whose
 *                    copybook has no user-type column
 */
public record MenuOption(int number, String name, String programName, String userType) {

    /** CDEMO-*-OPT-NAME as stored: PIC X(35), space padded. */
    public String paddedName() {
        return name.length() >= 35 ? name.substring(0, 35) : name + " ".repeat(35 - name.length());
    }

    /**
     * The option line BUILD-MENU-OPTIONS paints: {@code STRING num '. ' name}
     * into the map's X(40) field (COMEN01C.cbl:262-303, COADM01C.cbl:229-266).
     */
    public String displayText() {
        String text = String.format("%02d. %s", number, paddedName());
        return text.length() >= ScreenText.MENU_OPTION_LINE_LENGTH
                ? text.substring(0, ScreenText.MENU_OPTION_LINE_LENGTH)
                : text + " ".repeat(ScreenText.MENU_OPTION_LINE_LENGTH - text.length());
    }
}
