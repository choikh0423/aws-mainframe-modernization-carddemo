package com.carddemo.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The sign-on seam.
 *
 * <p>CardDemo's access control lived in the menus, not in the transactions:
 * COSGN00C established CDEMO-USER-TYPE, then COMEN01C refused an 'A' option to a
 * 'U' user and COADM01C was only ever reached by administrators. Individual
 * transactions did no further check. This filter chain reproduces that boundary:
 * {@code /api/admin/**} - the COADM01C family - requires the ADMIN role that
 * {@code AuthController} grants from SEC-USR-TYPE, and the remaining screen APIs
 * are reachable by any caller, exactly as the CICS transactions were.
 *
 * <p>CSRF is disabled because the React shell is a separate origin talking to a
 * JSON API; the session cookie is the only credential and it is same-site by
 * default.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .build();
    }
}
