package com.carddemo.authshell;

import com.carddemo.common.message.CardDemoMessages;
import com.carddemo.common.session.CommareaContext;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * COMEN01C / COADM01C option handling.
 *
 * <p>COMEN01C moves the typed option into {@code PIC 9(02)} after replacing spaces
 * with zeros, then rejects it when it is non-numeric, zero, or greater than
 * CDEMO-MENU-OPT-COUNT. An ordinary user ('U') choosing an option whose
 * CDEMO-MENU-OPT-USRTYPE is 'A' is refused before the XCTL happens.
 */
@Service
public class MenuService {

    public List<MenuOption> mainMenu() {
        return MenuCatalog.MAIN_MENU;
    }

    public List<MenuOption> adminMenu() {
        return MenuCatalog.ADMIN_MENU;
    }

    public MenuSelection selectMainMenuOption(String typedOption, String userType) {
        return select(MenuCatalog.MAIN_MENU, typedOption, userType);
    }

    public MenuSelection selectAdminMenuOption(String typedOption, String userType) {
        return select(MenuCatalog.ADMIN_MENU, typedOption, userType);
    }

    private MenuSelection select(List<MenuOption> options, String typedOption, String userType) {
        int number = parseOption(typedOption);
        if (number <= 0 || number > options.size()) {
            return MenuSelection.rejected(CardDemoMessages.MENU_INVALID_OPTION);
        }

        MenuOption option = options.get(number - 1);
        boolean ordinaryUser = !CommareaContext.USER_TYPE_ADMIN.equals(userType);
        if (ordinaryUser && CommareaContext.USER_TYPE_ADMIN.equals(option.userType())) {
            return MenuSelection.rejected(CardDemoMessages.MENU_ADMIN_ONLY);
        }
        return MenuSelection.accepted(option);
    }

    /**
     * MOVE OPTIONI TO WS-OPTION-X (PIC X(02) JUST RIGHT), INSPECT REPLACING ALL ' '
     * BY '0', MOVE WS-OPTION-X TO WS-OPTION (PIC 9(02)). Anything that is not two
     * digits after that substitution fails the IS NOT NUMERIC test.
     */
    private static int parseOption(String typedOption) {
        if (typedOption == null) {
            return 0;
        }
        String justifiedRight = typedOption.strip();
        if (justifiedRight.length() > 2) {
            return 0;
        }
        String zeroFilled = ("  " + justifiedRight)
                .substring(justifiedRight.length())
                .replace(' ', '0');
        for (int i = 0; i < zeroFilled.length(); i++) {
            if (!Character.isDigit(zeroFilled.charAt(i))) {
                return 0;
            }
        }
        return Integer.parseInt(zeroFilled);
    }
}
