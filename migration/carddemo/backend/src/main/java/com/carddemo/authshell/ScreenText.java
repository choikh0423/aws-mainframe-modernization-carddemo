package com.carddemo.authshell;

import java.util.List;

/**
 * The static text of the three S-01 maps, transcribed field by field from
 * {@code app/bms/COSGN00.bms}, {@code app/bms/COMEN01.bms} and
 * {@code app/bms/COADM01.bms}.
 *
 * <p>BMS held this text in the map, not in the program, and the terminal painted
 * it; here the backend serves it and the React screen paints it, so the literals
 * live in one place and are covered by tests instead of being retyped in JSX.
 */
public final class ScreenText {

    private ScreenText() {
    }

    /** CCDA-TITLE01 (app/cpy/COTTL01Y.cpy:18-19). */
    public static final String TITLE01 = "AWS Mainframe Modernization";
    /** CCDA-TITLE02 (app/cpy/COTTL01Y.cpy:20-21). */
    public static final String TITLE02 = "CardDemo";

    // ---------------------------------------------------------------- COSGN00A

    public static final String SIGNON_TRAN_ID = "CC00";
    public static final String SIGNON_PROGRAM = "COSGN00C";

    /** COSGN00.bms:75-79, POS=(3,1); the APPLID value field is 8 characters. */
    public static final String SIGNON_APPLID_LABEL = "AppID:";
    /** COSGN00.bms:84-88, POS=(3,64); the SYSID value field is 8 characters, initially blank. */
    public static final String SIGNON_SYSID_LABEL = "SysID:";

    /** COSGN00.bms:94-99, POS=(5,6). */
    public static final String SIGNON_BANNER =
            "This is a Credit Card Demo Application for Mainframe Modernization";

    /** COSGN00.bms:100-144, POS=(7,21) to (15,21) — nine 42-character lines. */
    public static final List<String> SIGNON_ART = List.of(
            "+========================================+",
            "|%%%%%%%  NATIONAL RESERVE NOTE  %%%%%%%%|",
            "|%(1)  THE UNITED STATES OF KICSLAND (1)%|",
            "|%$$              ___       ********  $$%|",
            "|%$    {x}       (o o)                 $%|",
            "|%$     ******  (  V  )      O N E     $%|",
            "|%(1)          ---m-m---             (1)%|",
            "|%%~~~~~~~~~~~ ONE DOLLAR ~~~~~~~~~~~~~%%|",
            "+========================================+");

    /** COSGN00.bms:145-150, POS=(17,16). */
    public static final String SIGNON_PROMPT =
            "Type your User ID and Password, then press ENTER:";
    /** COSGN00.bms:151-155, POS=(19,29). */
    public static final String SIGNON_USER_ID_LABEL = "User ID     :";
    /** COSGN00.bms:170-174, POS=(20,29). */
    public static final String SIGNON_PASSWORD_LABEL = "Password    :";
    /** COSGN00.bms:165-169 and :185-189, POS=(19,52) and (20,52). */
    public static final String SIGNON_FIELD_HINT = "(8 Char)";
    /** USERID / PASSWD are both LENGTH=8 (COSGN00.bms:156-160, :175-180). */
    public static final int SIGNON_FIELD_LENGTH = 8;
    /** COSGN00.bms:201-205, POS=(24,1). */
    public static final String SIGNON_PF_KEYS = "ENTER=Sign-on  F3=Exit";

    // ------------------------------------------------------- COMEN1A / COADM1A

    public static final String MAIN_MENU_TRAN_ID = "CM00";
    public static final String MAIN_MENU_PROGRAM = "COMEN01C";
    /** COMEN01.bms:75-79, POS=(4,35). */
    public static final String MAIN_MENU_TITLE = "Main Menu";

    public static final String ADMIN_MENU_TRAN_ID = "CA00";
    public static final String ADMIN_MENU_PROGRAM = "COADM01C";
    /** COADM01.bms:75-79, POS=(4,35). */
    public static final String ADMIN_MENU_TITLE = "Admin Menu";

    /** COMEN01.bms:140-144 / COADM01.bms:140-144, POS=(20,15). */
    public static final String MENU_PROMPT = "Please select an option :";
    /** OPTION is LENGTH=2, NUM, JUSTIFY=(RIGHT,ZERO) (COMEN01.bms:145-149). */
    public static final int MENU_OPTION_LENGTH = 2;
    /** COMEN01.bms:158-162 / COADM01.bms:158-162, POS=(24,1). */
    public static final String MENU_PF_KEYS = "ENTER=Continue  F3=Exit";
    /** OPTN001..OPTN012 are LENGTH=40 fields (COMEN01.bms:80-139). */
    public static final int MENU_OPTION_LINE_LENGTH = 40;
}
