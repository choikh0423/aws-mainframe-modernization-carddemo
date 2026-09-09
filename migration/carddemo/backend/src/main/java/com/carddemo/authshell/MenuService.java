package com.carddemo.authshell;

import com.carddemo.common.message.CardDemoMessages;
import com.carddemo.common.session.CommareaContext;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * COMEN01C and COADM01C option handling (COMEN01C.cbl:115-191,
 * COADM01C.cbl:119-158).
 *
 * <p>Both programs normalise the 2-byte OPTION field the same way, then reject
 * anything non-numeric, zero or above their table's count. From there the two
 * diverge: the main menu checks CDEMO-MENU-OPT-USRTYPE against an ordinary user,
 * INQUIREs COPAUS0C before transferring to it and has a green "coming soon"
 * message for DUMMY programs; the admin menu has no user-type check at all and
 * answers every DUMMY target — and every PGMIDERR from a target that is not
 * installed — with one message that does not even name the option.
 */
@Service
public class MenuService {

    private final ProgramInstallation programs;

    public MenuService(ProgramInstallation programs) {
        this.programs = programs;
    }

    public List<MenuOption> mainMenu() {
        return MenuCatalog.MAIN_MENU;
    }

    public List<MenuOption> adminMenu() {
        return MenuCatalog.ADMIN_MENU;
    }

    /** COMEN01C PROCESS-ENTER-KEY. */
    public MenuSelection selectMainMenuOption(String typedOption, String userType) {
        String echo = normalisedOption(typedOption);
        MenuOption option = validOption(MenuCatalog.MAIN_MENU, echo);
        if (option == null) {
            return MenuSelection.rejected(CardDemoMessages.MENU_INVALID_OPTION,
                    MessageColour.RED, echo);
        }

        if (isAdminOnlyFor(option, userType)) {
            return MenuSelection.rejected(CardDemoMessages.MENU_ADMIN_ONLY,
                    MessageColour.RED, echo);
        }

        if (MenuCatalog.INQUIRED_PROGRAM.equals(option.programName())) {
            // EXEC CICS INQUIRE PROGRAM(...) NOHANDLE - transfer only if defined.
            if (programs.isInstalled(option.programName())) {
                return MenuSelection.accepted(option, echo);
            }
            return MenuSelection.notDispatched(option,
                    CardDemoMessages.menuOptionNotInstalled(option.paddedName()),
                    MessageColour.RED, echo);
        }

        if (isDummy(option)) {
            return MenuSelection.notDispatched(option,
                    CardDemoMessages.menuOptionComingSoon(option.paddedName()),
                    MessageColour.GREEN, echo);
        }

        return MenuSelection.accepted(option, echo);
    }

    /** COADM01C PROCESS-ENTER-KEY, plus its PGMIDERR handler. */
    public MenuSelection selectAdminMenuOption(String typedOption, String userType) {
        String echo = normalisedOption(typedOption);
        MenuOption option = validOption(MenuCatalog.ADMIN_MENU, echo);
        if (option == null) {
            return MenuSelection.rejected(CardDemoMessages.MENU_INVALID_OPTION,
                    MessageColour.RED, echo);
        }

        // No user-type test exists in COADM01C: CA00 is reachable by whoever
        // COSGN00C routed here.
        if (isDummy(option) || !programs.isInstalled(option.programName())) {
            return MenuSelection.notDispatched(option,
                    CardDemoMessages.ADMIN_OPTION_NOT_INSTALLED, MessageColour.GREEN, echo);
        }
        return MenuSelection.accepted(option, echo);
    }

    /**
     * {@code IF CDEMO-USRTYP-USER AND CDEMO-MENU-OPT-USRTYPE(WS-OPTION) = 'A'}
     * (COMEN01C.cbl:136-137): the test is on 'U' exactly, not on "not an
     * administrator", so any other user type walks past it.
     */
    static boolean isAdminOnlyFor(MenuOption option, String userType) {
        return CommareaContext.USER_TYPE_USER.equals(userType)
                && CommareaContext.USER_TYPE_ADMIN.equals(option.userType());
    }

    private static boolean isDummy(MenuOption option) {
        return option.programName().startsWith(MenuCatalog.DUMMY_PREFIX);
    }

    /**
     * IF WS-OPTION IS NOT NUMERIC OR WS-OPTION > count OR WS-OPTION = ZEROS:
     * returns the selected row, or null when the typed value fails that test.
     */
    private static MenuOption validOption(List<MenuOption> options, String normalisedOption) {
        if (!isNumeric(normalisedOption)) {
            return null;
        }
        int number = Integer.parseInt(normalisedOption);
        if (number == 0 || number > options.size()) {
            return null;
        }
        return options.get(number - 1);
    }

    /**
     * The option field as PROCESS-ENTER-KEY leaves it (COMEN01C.cbl:117-125):
     * scan back to the last non-blank byte, MOVE that prefix to a PIC X(02)
     * JUST RIGHT field and INSPECT REPLACING ALL ' ' BY '0'. So {@code "1"},
     * {@code "1 "}, {@code " 1"} and {@code "01"} all end up as {@code "01"},
     * and the two digits are what the map echoes in OPTIONO.
     */
    static String normalisedOption(String typedOption) {
        String field = optionField(typedOption);
        int lastNonBlank = ScreenText.MENU_OPTION_LENGTH;
        while (lastNonBlank > 1 && field.charAt(lastNonBlank - 1) == ' ') {
            lastNonBlank--;
        }
        String prefix = field.substring(0, lastNonBlank);
        String justifiedRight = " ".repeat(ScreenText.MENU_OPTION_LENGTH - prefix.length()) + prefix;
        return justifiedRight.replace(' ', '0');
    }

    /** OPTIONI is a 2-byte field; a longer value cannot survive the RECEIVE MAP. */
    private static String optionField(String typedOption) {
        String value = typedOption == null ? "" : typedOption;
        if (value.length() > ScreenText.MENU_OPTION_LENGTH) {
            value = value.substring(value.length() - ScreenText.MENU_OPTION_LENGTH);
        }
        return value + " ".repeat(ScreenText.MENU_OPTION_LENGTH - value.length());
    }

    private static boolean isNumeric(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }
}
