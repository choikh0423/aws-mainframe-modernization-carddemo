package com.carddemo.common.session;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Holds the one {@link CommareaContext} that belongs to a signed-on user, in the
 * HTTP session. This is the seam CICS provided with the DFHCOMMAREA carried
 * across pseudo-conversational turns: a stream injects this, reads the fields
 * its COBOL program read from the commarea, and writes back the ones it set
 * before its {@code XCTL}.
 *
 * <p>The session is resolved on use, not at construction, so the application
 * also starts without a web server ({@code --spring.main.web-application-type=none},
 * the batch launch).
 */
@Component
public class CommareaSession {

    static final String ATTRIBUTE = "carddemo.commarea";

    private final ObjectProvider<HttpSession> httpSession;

    public CommareaSession(ObjectProvider<HttpSession> httpSession) {
        this.httpSession = httpSession;
    }

    /** The current commarea, created empty on first use. */
    public CommareaContext get() {
        CommareaContext commarea = (CommareaContext) httpSession.getObject().getAttribute(ATTRIBUTE);
        if (commarea == null) {
            commarea = new CommareaContext();
            httpSession.getObject().setAttribute(ATTRIBUTE, commarea);
        }
        return commarea;
    }

    public void put(CommareaContext commarea) {
        httpSession.getObject().setAttribute(ATTRIBUTE, commarea);
    }

    /** Drops the commarea, mirroring the sign-off path that returns to COSGN00C. */
    public void clear() {
        httpSession.getObject().removeAttribute(ATTRIBUTE);
    }
}
