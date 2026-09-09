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
 * HTTP-level FR-V1/FR-V2/FR-V3 coverage for {@link TransactionViewController}
 * against the seeded H2 DB. Confirms status codes + the exact legacy ERRMSG
 * text carried in the error body.
 *
 * MockMvc is built from the autowired {@link WebApplicationContext} (rather than
 * {@code @AutoConfigureMockMvc}) so this test shares the cached default
 * {@code @SpringBootTest} context with the rest of the suite — the in-memory H2
 * (DB_CLOSE_DELAY=-1) survives across contexts in one JVM, so a second context
 * would re-run data.sql and hit duplicate keys.
 */
@SpringBootTest
class TransactionViewControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void frV1_found_returns200WithAllFields() throws Exception {
        mockMvc.perform(get("/api/transactions/{id}", "0000000000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("0000000000000001"))
                .andExpect(jsonPath("$.cardNum").value("4111111111111111"))
                .andExpect(jsonPath("$.typeCd").value("01"))
                .andExpect(jsonPath("$.catCd").value(1000))
                .andExpect(jsonPath("$.source").value("POS"))
                .andExpect(jsonPath("$.description").value("GROCERY PURCHASE"))
                .andExpect(jsonPath("$.amount").value(13.75))
                .andExpect(jsonPath("$.amountDisplay").value("+00000013.75"))
                .andExpect(jsonPath("$.origTs").value("2023-06-01-10.15.31.000000"))
                .andExpect(jsonPath("$.procTs").value("2023-06-01-23.59.51.000000"))
                .andExpect(jsonPath("$.merchantId").value(900000001L))
                .andExpect(jsonPath("$.merchantName").value("ACME SUPERMARKET"))
                .andExpect(jsonPath("$.merchantCity").value("NEW YORK"))
                .andExpect(jsonPath("$.merchantZip").value("10001"));
    }

    @Test
    void frV2_notFound_returns404WithLegacyMessage() throws Exception {
        mockMvc.perform(get("/api/transactions/{id}", "9999999999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction ID NOT found..."));
    }

    @Test
    void frV3_blankId_returns400WithLegacyMessage() throws Exception {
        mockMvc.perform(get("/api/transactions/{id}", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tran ID can NOT be empty..."));
    }
}
