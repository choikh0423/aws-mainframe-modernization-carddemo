package com.carddemo.user.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-US-1 — S-05 is the COADM01C family, so every CU00..CU03 endpoint sits under
 * {@code /api/admin/**} and is reachable only with the ADMIN role that sign-on
 * grants from SEC-USR-TYPE = 'A'. This is the boundary the shared
 * {@code SecurityConfig} already enforces; the stream adds no security code of
 * its own. Runs with the security filter chain applied.
 */
@SpringBootTest
@Transactional
class UserAdminSecurityTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithAnonymousUser
    void frUS1_anonymousCallerIsRefused() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "USER0001", roles = "USER")
    void frUS1_nonAdminIsRefusedOnEveryEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users/USER0001")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/users/USER0001").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/users/USER0001")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "ADMIN001", roles = "ADMIN")
    void frUS1_adminIsAdmitted() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users/USER0001")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/users/USER0005")).andExpect(status().isOk());
    }
}
