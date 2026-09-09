package com.carddemo.user.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level FR-UD-6..FR-UD-9 coverage for {@link UserDeleteController}. Rolled
 * back so the deleted fixture record returns for the other streams.
 */
@SpringBootTest
@Transactional
class UserDeleteControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void frUD8_existingUser_returns200AndTheDeletedConfirmation() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{id}", "USER0005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("USER0005"))
                .andExpect(jsonPath("$.message").value("User USER0005 has been deleted ..."));
    }

    @Test
    void frUD9_unknownId_returns404() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{id}", "NOSUCH01"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User ID NOT found..."));
    }

    @Test
    void frUD6_blankKey_returns400() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{id}", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("User ID can NOT be empty..."));
    }
}
