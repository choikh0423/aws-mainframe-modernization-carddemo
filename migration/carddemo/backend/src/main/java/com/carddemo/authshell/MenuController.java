package com.carddemo.authshell;

import com.carddemo.common.session.CommareaContext;
import com.carddemo.common.session.CommareaSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The COMEN01C main menu and the COADM01C admin menu. */
@RestController
@RequestMapping("/api/menu")
public class MenuController {

    private final MenuService menuService;
    private final CommareaSession commareaSession;

    public MenuController(MenuService menuService, CommareaSession commareaSession) {
        this.menuService = menuService;
        this.commareaSession = commareaSession;
    }

    /** The typed OPTIONI field of COMEN1AI / COADM1AI. */
    public record MenuSelectionRequest(String option) {
    }

    /** The XCTL target, or the message the screen shows instead. */
    public record MenuSelectionResponse(boolean accepted, Integer optionNumber, String optionName,
                                        String programName, String message) {
    }

    @GetMapping("/main")
    public List<MenuOption> mainMenu() {
        return menuService.mainMenu();
    }

    @GetMapping("/admin")
    public List<MenuOption> adminMenu() {
        return menuService.adminMenu();
    }

    @PostMapping("/main/select")
    public ResponseEntity<MenuSelectionResponse> selectMainMenuOption(
            @RequestBody MenuSelectionRequest request) {
        return respond(commarea ->
                menuService.selectMainMenuOption(request.option(), commarea.getUserType()));
    }

    @PostMapping("/admin/select")
    public ResponseEntity<MenuSelectionResponse> selectAdminMenuOption(
            @RequestBody MenuSelectionRequest request) {
        return respond(commarea ->
                menuService.selectAdminMenuOption(request.option(), commarea.getUserType()));
    }

    private ResponseEntity<MenuSelectionResponse> respond(
            java.util.function.Function<CommareaContext, MenuSelection> selector) {
        CommareaContext commarea = commareaSession.get();
        if (commarea == null || commarea.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        MenuSelection selection = selector.apply(commarea);
        if (!selection.accepted()) {
            return ResponseEntity.ok(new MenuSelectionResponse(
                    false, null, null, null, selection.message()));
        }

        MenuOption option = selection.option();
        commarea.transferTo(commarea.getToTranId(), option.programName());
        commarea.setPgmContext(CommareaContext.PGM_CONTEXT_ENTER);
        commareaSession.put(commarea);
        return ResponseEntity.ok(new MenuSelectionResponse(
                true, option.number(), option.name(), option.programName(), ""));
    }
}
