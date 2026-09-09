package com.carddemo.authshell;

import com.carddemo.common.message.CardDemoMessages;
import com.carddemo.common.session.CommareaContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The COSGN00C screen: sign on, sign off, and read back the current COMMAREA. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SignOnService signOnService;
    private final ShellSession shellSession;
    private final SignOnScreenResponse signOnScreen;

    /**
     * {@code EXEC CICS ASSIGN APPLID/SYSID} (COSGN00C.cbl:198-204) named the CICS
     * region the operator had reached. The consolidated app has no region, so the
     * two 8-character fields are configuration: the deployment name and, like the
     * map's own INITIAL, nothing at all until an operator sets one.
     */
    public AuthController(SignOnService signOnService, ShellSession shellSession,
                          @Value("${carddemo.authshell.applid:CARDDEMO}") String applid,
                          @Value("${carddemo.authshell.sysid:}") String sysid) {
        this.signOnService = signOnService;
        this.shellSession = shellSession;
        this.signOnScreen = new SignOnScreenResponse(
                ScreenText.SIGNON_TRAN_ID, ScreenText.SIGNON_PROGRAM,
                ScreenText.TITLE01, ScreenText.TITLE02,
                ScreenText.SIGNON_APPLID_LABEL, applid,
                ScreenText.SIGNON_SYSID_LABEL, sysid,
                ScreenText.SIGNON_BANNER, ScreenText.SIGNON_ART, ScreenText.SIGNON_PROMPT,
                ScreenText.SIGNON_USER_ID_LABEL, ScreenText.SIGNON_PASSWORD_LABEL,
                ScreenText.SIGNON_FIELD_HINT, ScreenText.SIGNON_FIELD_LENGTH,
                ScreenText.SIGNON_PF_KEYS);
    }

    /**
     * The sign-on screen's two input fields, USERIDI and PASSWDI of COSGN0AI,
     * plus the EIBAID the map was submitted with (absent means DFHENTER).
     */
    public record SignOnRequest(String userId, String password, String aid) {
    }

    /**
     * The COMMAREA as the next screen would receive it: CDEMO-USER-ID,
     * CDEMO-USER-TYPE, CDEMO-FROM-TRANID, CDEMO-FROM-PROGRAM, CDEMO-TO-PROGRAM
     * and CDEMO-PGM-CONTEXT, plus the ERRMSG line when there is no session.
     */
    public record SessionResponse(String userId, String userType, String fromTranId,
                                  String fromProgram, String nextProgram, Integer pgmContext,
                                  String message, String errorField) {

        static SessionResponse of(CommareaContext commarea) {
            return new SessionResponse(commarea.getUserId(), commarea.getUserType(),
                    commarea.getFromTranId(), commarea.getFromProgram(),
                    commarea.getToProgram(), commarea.getPgmContext(), "", null);
        }

        static SessionResponse message(String message, String errorField) {
            return new SessionResponse(null, null, null, null, null, null, message, errorField);
        }
    }

    /** The static text of COSGN00A plus the header fields COSGN00C ASSIGNs. */
    public record SignOnScreenResponse(String tranId, String programName, String title01,
                                       String title02, String applidLabel, String applid,
                                       String sysidLabel, String sysid,
                                       String banner, List<String> art, String prompt,
                                       String userIdLabel, String passwordLabel,
                                       String fieldHint, int fieldLength, String pfKeys) {
    }

    /** The map's own text, as BMS painted it before the program ever ran. */
    @GetMapping("/screen")
    public SignOnScreenResponse screen() {
        return signOnScreen;
    }

    @PostMapping("/signon")
    public ResponseEntity<SessionResponse> signOn(@RequestBody SignOnRequest request,
                                                  HttpServletRequest httpRequest,
                                                  HttpServletResponse httpResponse) {
        switch (Aid.of(request.aid())) {
            case PF3:
                // WHEN DFHPF3: SEND TEXT CCDA-MSG-THANK-YOU and RETURN.
                return ResponseEntity.ok(signOff(httpRequest));
            case OTHER:
                // WHEN OTHER: redisplay COSGN0A with CCDA-MSG-INVALID-KEY.
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(SessionResponse.message(CardDemoMessages.INVALID_KEY, null));
            default:
                break;
        }

        SignOnResult result = signOnService.signOn(request.userId(), request.password());
        if (!result.signedOn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(SessionResponse.message(result.message(), result.errorField()));
        }

        CommareaContext commarea = signOnService.toCommarea(result);
        shellSession.start(commarea, httpRequest, httpResponse);

        return ResponseEntity.ok(SessionResponse.of(commarea));
    }

    /** PF3 on the sign-on screen: CCDA-MSG-THANK-YOU and the session goes away. */
    @PostMapping("/signoff")
    public SessionResponse signOff(HttpServletRequest httpRequest) {
        shellSession.end(httpRequest);
        return SessionResponse.message(CardDemoMessages.THANK_YOU, null);
    }

    @GetMapping("/session")
    public ResponseEntity<SessionResponse> currentSession() {
        CommareaContext commarea = shellSession.current();
        if (commarea == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(SessionResponse.of(commarea));
    }
}
