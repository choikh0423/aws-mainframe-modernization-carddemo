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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level FR-UU-2..FR-UU-10 coverage for {@link UserUpdateController} — the
 * CU02 ENTER fetch (also used by the CU03 screen) and the PF5 rewrite.
 */
@SpringBootTest
@Transactional
class UserUpdateControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    private static String body(String fn, String ln, String pwd, String type) {
        return String.format(
                "{\"firstName\":\"%s\",\"lastName\":\"%s\",\"password\":\"%s\","
                        + "\"userType\":\"%s\"}",
                fn, ln, pwd, type);
    }

    @Test
    void frUU3_fetch_returns200WithTheRecord() throws Exception {
        mockMvc.perform(get("/api/admin/users/{id}", "USER0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("USER0001"))
                .andExpect(jsonPath("$.firstName").value("LAWRENCE"))
                .andExpect(jsonPath("$.lastName").value("THOMAS"))
                .andExpect(jsonPath("$.userType").value("U"));
    }

    @Test
    void frUU4_fetchOfAnUnknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/admin/users/{id}", "NOSUCH01"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User ID NOT found..."));
    }

    @Test
    void frUU2_fetchWithABlankKey_returns400() throws Exception {
        mockMvc.perform(get("/api/admin/users/{id}", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("User ID can NOT be empty..."));
    }

    @Test
    void frUU9_changedRecord_returns200AndTheUpdatedConfirmation() throws Exception {
        mockMvc.perform(put("/api/admin/users/{id}", "USER0004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("AVERARDO", "MAZZI", "NEWPASS1", "U")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User USER0004 has been updated ..."));
    }

    @Test
    void frUU8_unchangedRecord_returns400PleaseModifyToUpdate() throws Exception {
        mockMvc.perform(put("/api/admin/users/{id}", "USER0004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("AVERARDO", "MAZZI", "PASSWORD", "U")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please modify to update ..."));
    }

    @Test
    void frUU16_updateOfAnUnknownId_returns404() throws Exception {
        mockMvc.perform(put("/api/admin/users/{id}", "NOSUCH01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("A", "B", "C", "U")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User ID NOT found..."));
    }

    @Test
    void frUU6_blankFieldOnUpdate_returns400WithTheLegacyLiteral() throws Exception {
        mockMvc.perform(put("/api/admin/users/{id}", "USER0004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("AVERARDO", "MAZZI", "", "U")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password can NOT be empty..."));
    }
}
