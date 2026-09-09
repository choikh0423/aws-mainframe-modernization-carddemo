package com.carddemo.authshell;

import com.carddemo.common.session.CommareaContext;
import com.carddemo.common.session.CommareaSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The pseudo-conversation itself: the COMMAREA that survives between turns plus
 * the authority CICS derived from the signed-on user's SEC-USR-TYPE.
 *
 * <p>{@link #start} is what a successful COSGN00C sign-on leaves behind and
 * {@link #end} is the {@code XCTL PROGRAM('COSGN00C')} with no COMMAREA that PF3
 * performs on all three screens — after it, the next turn starts at
 * {@code EIBCALEN = 0}.
 */
@Component
public class ShellSession {

    private final CommareaSession commareaSession;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public ShellSession(CommareaSession commareaSession) {
        this.commareaSession = commareaSession;
    }

    /** The COMMAREA of the current turn, or null when EIBCALEN would be 0. */
    public CommareaContext current() {
        CommareaContext commarea = commareaSession.get();
        return commarea == null || commarea.getUserId() == null ? null : commarea;
    }

    public void save(CommareaContext commarea) {
        commareaSession.put(commarea);
    }

    public void start(CommareaContext commarea, HttpServletRequest request,
                      HttpServletResponse response) {
        commareaSession.put(commarea);
        String role = commarea.isAdmin() ? "ROLE_ADMIN" : "ROLE_USER";
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                commarea.getUserId(), null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    public void end(HttpServletRequest request) {
        commareaSession.clear();
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
    }
}
