package com.carddemo.pendingauth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The clock the PendingAuthorizations services read instead of
 * {@code EXEC CICS ASKTIME}: the authorization key COPAUA0C builds and the
 * fraud report date COPAUS2C stamps both come from the CICS clock, and tests
 * need to pin them. Declared {@link ConditionalOnMissingBean} so another stream
 * may contribute the application-wide clock later without a conflict.
 */
@Configuration
public class PendingAuthClockConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock carddemoClock() {
        return Clock.systemDefaultZone();
    }
}
