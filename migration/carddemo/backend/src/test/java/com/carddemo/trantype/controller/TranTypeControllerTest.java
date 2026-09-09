package com.carddemo.trantype.controller;

import com.carddemo.trantype.message.TranTypeMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The two CTLI/CTTU endpoints over HTTP against the seeded database (seven
 * transaction types), including the {@code /api/admin/**} guard that stands in
 * for CICS reaching these transactions only through COADM01C.
 *
 * <p>Both requests are first-entry sends, so nothing in the shared context is
 * modified. Covers FR-L01 and FR-U01 over HTTP, plus the admin gate.
 */
@SpringBootTest
class TranTypeControllerTest {

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
    @WithMockUser(roles = "ADMIN")
    void listReturnsTheFirstPageOfTheMap() throws Exception {
        mockMvc.perform(post("/api/admin/transaction-types/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aid\":\"ENTER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(7))
                .andExpect(jsonPath("$.rows[0].typeCode").value("01"))
                .andExpect(jsonPath("$.rows[0].description").value("Purchase"))
                .andExpect(jsonPath("$.pageNumber").value(1))
                .andExpect(jsonPath("$.infoMessage").value(TranTypeMessages.LIST_INFO_REC_ACTIONS))
                .andExpect(jsonPath("$.nextProgram").value("COTRTLIC"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateAsksForTheKeyOnFirstEntry() throws Exception {
        mockMvc.perform(post("/api/admin/transaction-types/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aid\":\"ENTER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeCode").value(""))
                .andExpect(jsonPath("$.infoMessage").value(TranTypeMessages.UPD_INFO_ENTER_KEY))
                .andExpect(jsonPath("$.nextProgram").value("COTRTUPC"))
                .andExpect(jsonPath("$.state.programReenter").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void aNonAdministratorCannotReachTheMaintenanceScreens() throws Exception {
        mockMvc.perform(post("/api/admin/transaction-types/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aid\":\"ENTER\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAnonymousCallerCannotReachTheMaintenanceScreens() throws Exception {
        mockMvc.perform(post("/api/admin/transaction-types/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aid\":\"ENTER\"}"))
                .andExpect(status().is4xxClientError());
    }
}
