package com.carddemo.authshell;

import java.util.List;

/**
 * The menu option tables, transcribed from the copybooks that define them:
 * {@code app/cpy/COMEN02Y.cpy} (CARDDEMO-MAIN-MENU-OPTIONS, CDEMO-MENU-OPT-COUNT
 * = 11) and {@code app/cpy/COADM02Y.cpy} (CARDDEMO-ADMIN-MENU-OPTIONS,
 * CDEMO-ADMIN-OPT-COUNT = 6).
 *
 * <p>Both copybooks declare more occurrences than they populate (12 and 9); the
 * count field is the only guard the programs use, so the catalog carries exactly
 * the populated rows. The copybook's X(35) name padding is reproduced by
 * {@link MenuOption#paddedName()} where the COBOL depends on it.
 */
public final class MenuCatalog {

    /** COMEN02Y.cpy:23-92 — every option's CDEMO-MENU-OPT-USRTYPE is 'U'. */
    public static final List<MenuOption> MAIN_MENU = List.of(
            new MenuOption(1, "Account View", "COACTVWC", "U"),
            new MenuOption(2, "Account Update", "COACTUPC", "U"),
            new MenuOption(3, "Credit Card List", "COCRDLIC", "U"),
            new MenuOption(4, "Credit Card View", "COCRDSLC", "U"),
            new MenuOption(5, "Credit Card Update", "COCRDUPC", "U"),
            new MenuOption(6, "Transaction List", "COTRN00C", "U"),
            new MenuOption(7, "Transaction View", "COTRN01C", "U"),
            new MenuOption(8, "Transaction Add", "COTRN02C", "U"),
            new MenuOption(9, "Transaction Reports", "CORPT00C", "U"),
            new MenuOption(10, "Bill Payment", "COBIL00C", "U"),
            new MenuOption(11, "Pending Authorization View", "COPAUS0C", "U"));

    /** COADM02Y.cpy:26-54 — CDEMO-ADMIN-OPT has no user-type column at all. */
    public static final List<MenuOption> ADMIN_MENU = List.of(
            new MenuOption(1, "User List (Security)", "COUSR00C", null),
            new MenuOption(2, "User Add (Security)", "COUSR01C", null),
            new MenuOption(3, "User Update (Security)", "COUSR02C", null),
            new MenuOption(4, "User Delete (Security)", "COUSR03C", null),
            new MenuOption(5, "Transaction Type List/Update (Db2)", "COTRTLIC", null),
            new MenuOption(6, "Transaction Type Maintenance (Db2)", "COTRTUPC", null));

    /**
     * The one program COMEN01C checks with {@code EXEC CICS INQUIRE PROGRAM}
     * before transferring to it (COMEN01C.cbl:147-151).
     */
    public static final String INQUIRED_PROGRAM = "COPAUS0C";

    /** The prefix that marks a placeholder program in both tables. */
    public static final String DUMMY_PREFIX = "DUMMY";

    private MenuCatalog() {
    }
}
