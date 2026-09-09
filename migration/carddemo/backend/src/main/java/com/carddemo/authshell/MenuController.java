package com.carddemo.authshell;

import com.carddemo.common.message.CardDemoMessages;
import com.carddemo.common.session.CommareaContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.BiFunction;

/**
 * The COMEN01C main menu (CM00) and the COADM01C admin menu (CA00).
 *
 * <p>CA00 lives under {@code /api/admin} because that is the prefix the shared
 * filter chain guards with ROLE_ADMIN: COADM01C itself never re-checked the user
 * type, it was simply unreachable unless COSGN00C had routed an 'A' user to it.
 */
@RestController
@RequestMapping("/api")
public class MenuController {

    private final MenuService menuService;
    private final ShellSession shellSession;

    public MenuController(MenuService menuService, ShellSession shellSession) {
        this.menuService = menuService;
        this.shellSession = shellSession;
    }

    /** The typed OPTIONI field of COMEN1AI / COADM1AI, plus the EIBAID. */
    public record MenuSelectionRequest(String option, String aid) {
    }

    /** The XCTL target, or the message the screen shows instead. */
    public record MenuSelectionResponse(boolean accepted, Integer optionNumber, String optionName,
                                        String programName, String message, String messageColour,
                                        String optionEcho) {
    }

    /** One CDEMO-*-OPT row as the map's 40-character option line shows it. */
    public record MenuOptionView(int number, String name, String programName, String userType,
                                 String displayText) {
    }

    /** The map's static text plus the option lines the program builds into it. */
    public record MenuScreenResponse(String tranId, String programName, String title01,
                                     String title02, String title, String prompt,
                                     int optionLength, String pfKeys,
                                     List<MenuOptionView> options) {
    }

    @GetMapping("/menu/main")
    public ResponseEntity<MenuScreenResponse> mainMenu() {
        return screen(ScreenText.MAIN_MENU_TRAN_ID, ScreenText.MAIN_MENU_PROGRAM,
                ScreenText.MAIN_MENU_TITLE, menuService.mainMenu());
    }

    @GetMapping("/admin/menu")
    public ResponseEntity<MenuScreenResponse> adminMenu() {
        return screen(ScreenText.ADMIN_MENU_TRAN_ID, ScreenText.ADMIN_MENU_PROGRAM,
                ScreenText.ADMIN_MENU_TITLE, menuService.adminMenu());
    }

    @PostMapping("/menu/main/select")
    public ResponseEntity<MenuSelectionResponse> selectMainMenuOption(
            @RequestBody MenuSelectionRequest request, HttpServletRequest httpRequest) {
        return select(request, httpRequest, ScreenText.MAIN_MENU_TRAN_ID,
                ScreenText.MAIN_MENU_PROGRAM, menuService::selectMainMenuOption);
    }

    @PostMapping("/admin/menu/select")
    public ResponseEntity<MenuSelectionResponse> selectAdminMenuOption(
            @RequestBody MenuSelectionRequest request, HttpServletRequest httpRequest) {
        return select(request, httpRequest, ScreenText.ADMIN_MENU_TRAN_ID,
                ScreenText.ADMIN_MENU_PROGRAM, menuService::selectAdminMenuOption);
    }

    /**
     * The first paint of the menu: {@code IF NOT CDEMO-PGM-REENTER SET
     * CDEMO-PGM-REENTER TO TRUE ... SEND-MENU-SCREEN} (COMEN01C.cbl:87-90).
     * Without a COMMAREA the program returns to COSGN00C instead (:82-84).
     */
    private ResponseEntity<MenuScreenResponse> screen(String tranId, String programName,
                                                      String title, List<MenuOption> options) {
        CommareaContext commarea = shellSession.current();
        if (commarea == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        commarea.setPgmContext(CommareaContext.PGM_CONTEXT_REENTER);
        shellSession.save(commarea);

        return ResponseEntity.ok(new MenuScreenResponse(tranId, programName,
                ScreenText.TITLE01, ScreenText.TITLE02, title, ScreenText.MENU_PROMPT,
                ScreenText.MENU_OPTION_LENGTH, ScreenText.MENU_PF_KEYS,
                options.stream().map(MenuController::view).toList()));
    }

    private ResponseEntity<MenuSelectionResponse> select(
            MenuSelectionRequest request, HttpServletRequest httpRequest, String tranId,
            String programName, BiFunction<String, String, MenuSelection> selector) {

        CommareaContext commarea = shellSession.current();
        if (commarea == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        switch (Aid.of(request.aid())) {
            case PF3:
                // XCTL PROGRAM(CDEMO-TO-PROGRAM) with no COMMAREA: the sign-on
                // screen restarts at EIBCALEN = 0, so nothing survives.
                shellSession.end(httpRequest);
                return ResponseEntity.ok(new MenuSelectionResponse(true, null, null,
                        ScreenText.SIGNON_PROGRAM, "", MessageColour.RED.name(), null));
            case OTHER:
                return ResponseEntity.ok(new MenuSelectionResponse(false, null, null, null,
                        CardDemoMessages.INVALID_KEY, MessageColour.RED.name(), null));
            default:
                break;
        }

        MenuSelection selection = selector.apply(request.option(), commarea.getUserType());
        MenuOption option = selection.option();
        if (!selection.accepted()) {
            return ResponseEntity.ok(new MenuSelectionResponse(false,
                    option == null ? null : option.number(),
                    option == null ? null : option.name(),
                    option == null ? null : option.programName(),
                    selection.message(), selection.messageColour().name(),
                    selection.optionEcho()));
        }

        // MOVE WS-TRANID TO CDEMO-FROM-TRANID, WS-PGMNAME TO CDEMO-FROM-PROGRAM,
        // ZEROS TO CDEMO-PGM-CONTEXT, then XCTL to the option's program. The user
        // id and type stay as COSGN00C set them.
        commarea.setFromTranId(tranId);
        commarea.setFromProgram(programName);
        commarea.setToProgram(option.programName());
        commarea.setPgmContext(CommareaContext.PGM_CONTEXT_ENTER);
        shellSession.save(commarea);

        return ResponseEntity.ok(new MenuSelectionResponse(true, option.number(), option.name(),
                option.programName(), "", selection.messageColour().name(),
                selection.optionEcho()));
    }

    private static MenuOptionView view(MenuOption option) {
        return new MenuOptionView(option.number(), option.name(), option.programName(),
                option.userType(), option.displayText());
    }
}
