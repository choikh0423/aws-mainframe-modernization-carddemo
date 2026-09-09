package com.carddemo.authshell;

import com.carddemo.common.message.CardDemoMessages;
import com.carddemo.common.session.CommareaContext;
import com.carddemo.common.session.CommareaSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
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
    private final CommareaSession commareaSession;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AuthController(SignOnService signOnService, CommareaSession commareaSession) {
        this.signOnService = signOnService;
        this.commareaSession = commareaSession;
    }

    /** The sign-on screen's two input fields, USERIDI and PASSWDI of COSGN0AI. */
    public record SignOnRequest(String userId, String password) {
    }

    /** What the shell needs to draw the next screen. */
    public record SessionResponse(String userId, String userType, String nextProgram,
                                  String message, String errorField) {
    }

    @PostMapping("/signon")
    public ResponseEntity<SessionResponse> signOn(@RequestBody SignOnRequest request,
                                                  HttpServletRequest httpRequest,
                                                  HttpServletResponse httpResponse) {
        SignOnResult result = signOnService.signOn(request.userId(), request.password());
        if (!result.signedOn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new SessionResponse(
                    null, null, null, result.message(), result.errorField()));
        }

        CommareaContext commarea = signOnService.toCommarea(result);
        commareaSession.put(commarea);
        establishSecurityContext(result, httpRequest, httpResponse);

        return ResponseEntity.ok(new SessionResponse(
                result.userId(), result.userType(), result.nextProgram(), "", null));
    }

    /** PF3 on the sign-on screen: CCDA-MSG-THANK-YOU and the session goes away. */
    @PostMapping("/signoff")
    public SessionResponse signOff(HttpServletRequest httpRequest) {
        commareaSession.clear();
        SecurityContextHolder.clearContext();
        if (httpRequest.getSession(false) != null) {
            httpRequest.getSession(false).invalidate();
        }
        return new SessionResponse(null, null, null, CardDemoMessages.THANK_YOU, null);
    }

    @GetMapping("/session")
    public ResponseEntity<SessionResponse> currentSession() {
        CommareaContext commarea = commareaSession.get();
        if (commarea == null || commarea.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new SessionResponse(
                commarea.getUserId(), commarea.getUserType(), commarea.getToProgram(), "", null));
    }

    private void establishSecurityContext(SignOnResult result, HttpServletRequest request,
                                          HttpServletResponse response) {
        String role = CommareaContext.USER_TYPE_ADMIN.equals(result.userType())
                ? "ROLE_ADMIN"
                : "ROLE_USER";
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                result.userId(), null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
