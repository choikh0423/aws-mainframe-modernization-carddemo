package com.carddemo.authshell;

import java.util.List;

/**
 * The menu option tables, transcribed from the copybooks that define them:
 * {@code app/cpy/COMEN02Y.cpy} (CARDDEMO-MAIN-MENU-OPTIONS, 11 options) and
 * {@code app/cpy/COADM02Y.cpy} (CARDDEMO-ADMIN-MENU-OPTIONS, 6 options).
 *
 * <p>Option names keep the copybook's exact wording; the copybook's PIC X(35)
 * padding is not reproduced because the option name is rendered, not moved into
 * a fixed-length field.
 */
public final class MenuCatalog {

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

    /** COADM02Y carries no CDEMO-ADMIN-OPT-USRTYPE: the admin menu is admin-only in full. */
    public static final List<MenuOption> ADMIN_MENU = List.of(
            new MenuOption(1, "User List (Security)", "COUSR00C", "A"),
            new MenuOption(2, "User Add (Security)", "COUSR01C", "A"),
            new MenuOption(3, "User Update (Security)", "COUSR02C", "A"),
            new MenuOption(4, "User Delete (Security)", "COUSR03C", "A"),
            new MenuOption(5, "Transaction Type List/Update (Db2)", "COTRTLIC", "A"),
            new MenuOption(6, "Transaction Type Maintenance (Db2)", "COTRTUPC", "A"));

    private MenuCatalog() {
    }
}
