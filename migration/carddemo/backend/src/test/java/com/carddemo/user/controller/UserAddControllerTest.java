package com.carddemo.user.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level FR-UA-2, FR-UA-4, FR-UA-5 coverage for {@link UserAddController}: the exact
 * ERRMSG literal of the first failing edit, the duplicate-key response and the
 * green confirmation. Rolled back so the USRSEC fixture is left as seeded.
 */
@SpringBootTest
@Transactional
class UserAddControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    private static String body(String fn, String ln, String id, String pwd, String type) {
        return String.format(
                "{\"firstName\":\"%s\",\"lastName\":\"%s\",\"userId\":\"%s\","
                        + "\"password\":\"%s\",\"userType\":\"%s\"}",
                fn, ln, id, pwd, type);
    }

    @Test
    void frUA4_validRequest_returns201AndTheAddedConfirmation() throws Exception {
        mockMvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content(body("JOHN", "DOE", "NEWUSR01", "PASSWD01", "U")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("NEWUSR01"))
                .andExpect(jsonPath("$.message").value("User NEWUSR01 has been added ..."));
    }

    @Test
    void frUA2_blankFirstName_returns400WithTheLegacyLiteral() throws Exception {
        mockMvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "DOE", "NEWUSR01", "PASSWD01", "U")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("First Name can NOT be empty..."));
    }

    @Test
    void frUA2_blankUserType_returns400WithTheLegacyLiteral() throws Exception {
        mockMvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content(body("JOHN", "DOE", "NEWUSR01", "PASSWD01", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("User Type can NOT be empty..."));
    }

    @Test
    void frUA5_duplicateId_returns409UserIdAlreadyExist() throws Exception {
        mockMvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content(body("JOHN", "DOE", "ADMIN001", "PASSWD01", "U")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("User ID already exist..."));
    }
}
