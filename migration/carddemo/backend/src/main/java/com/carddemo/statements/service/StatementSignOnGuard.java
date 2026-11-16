package com.carddemo.statements.service;

import com.carddemo.statements.StatementMessages;
import com.carddemo.statements.exception.StatementSignOnRequiredException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Statements are customer data, so unlike the legacy screens (reachable by any caller, see
 * {@code SecurityConfig}) they need the authentication a COSGN00C sign-on stores in the session.
 * The shared filter chain is read-only for streams, so the check lives here.
 */
@Component
public class StatementSignOnGuard {

    public void requireSignedOn() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new StatementSignOnRequiredException(StatementMessages.SIGN_ON_REQUIRED);
        }
    }
}
