package com.carddemo.transaction.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level FR-L1/FR-L2/FR-L3/FR-L4/FR-L7 coverage for
 * {@link TransactionListController} against the seeded H2 DB (30 transactions).
 * Confirms the paged payload, boundary flags, and the exact legacy ERRMSG text.
 *
 * MockMvc is built from the autowired {@link WebApplicationContext} (not
 * {@code @AutoConfigureMockMvc}) so this test reuses the cached default
 * {@code @SpringBootTest} context, matching the Wave B tests and avoiding a
 * second context re-running data.sql against the shared in-memory H2.
 */
@SpringBootTest
class TransactionListControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void frL1_firstPage_returns200With10Rows() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(10))
                .andExpect(jsonPath("$.rows.length()").value(10))
                .andExpect(jsonPath("$.firstId").value("0000000000000001"))
                .andExpect(jsonPath("$.lastId").value("0000000000000010"))
                .andExpect(jsonPath("$.hasPrevPage").value(false))
                .andExpect(jsonPath("$.hasNextPage").value(true))
                .andExpect(jsonPath("$.rows[0].id").value("0000000000000001"))
                .andExpect(jsonPath("$.rows[0].date").value("06/01/23"))
                .andExpect(jsonPath("$.rows[0].description").value("GROCERY PURCHASE"))
                .andExpect(jsonPath("$.rows[0].amountDisplay").value("+00000013.75"))
                .andExpect(jsonPath("$.rows[3].amountDisplay").value("-00000025.00"));
    }

    @Test
    void frL2_startIdFilter_beginsAtThatTranId() throws Exception {
        mockMvc.perform(get("/api/transactions").param("startId", "0000000000000005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(10))
                .andExpect(jsonPath("$.firstId").value("0000000000000005"))
                .andExpect(jsonPath("$.lastId").value("0000000000000014"));
    }

    @Test
    void frL3_next_pagesForward() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("startId", "0000000000000010").param("dir", "next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstId").value("0000000000000011"))
                .andExpect(jsonPath("$.lastId").value("0000000000000020"))
                .andExpect(jsonPath("$.hasPrevPage").value(true))
                .andExpect(jsonPath("$.hasNextPage").value(true));
    }

    @Test
    void frL3_next_lastPageHasNoNext() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("startId", "0000000000000020").param("dir", "next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstId").value("0000000000000021"))
                .andExpect(jsonPath("$.lastId").value("0000000000000030"))
                .andExpect(jsonPath("$.hasNextPage").value(false));
    }

    @Test
    void frL4_prev_pagesBackward() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("startId", "0000000000000021").param("dir", "prev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstId").value("0000000000000011"))
                .andExpect(jsonPath("$.lastId").value("0000000000000020"))
                .andExpect(jsonPath("$.hasPrevPage").value(true))
                .andExpect(jsonPath("$.hasNextPage").value(true));
    }

    @Test
    void frL4_prev_toFirstPageHasNoPrev() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("startId", "0000000000000011").param("dir", "prev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstId").value("0000000000000001"))
                .andExpect(jsonPath("$.lastId").value("0000000000000010"))
                .andExpect(jsonPath("$.hasPrevPage").value(false));
    }

    @Test
    void frL7_nonNumericFilter_returns400WithLegacyMessage() throws Exception {
        mockMvc.perform(get("/api/transactions").param("startId", "12AB"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tran ID must be Numeric ..."));
    }
}
