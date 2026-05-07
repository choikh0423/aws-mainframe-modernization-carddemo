package com.carddemo.transaction.integration;

import com.carddemo.transaction.TransactionAddApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for TransactionAddController.
 * Uses full Spring Boot context with H2 database, seed data,
 * and real HTTP round-trips via MockMvc.
 *
 * Each test verifies end-to-end behavior matching COBOL CICS flow.
 */
@SpringBootTest(classes = TransactionAddApplication.class)
@AutoConfigureMockMvc
class TransactionAddControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Map<String, Object> makeValidBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("acctId", "1");
        body.put("typeCd", "01");
        body.put("catCd", "5001");
        body.put("source", "MANUAL");
        body.put("description", "Integration test transaction");
        body.put("amount", "+00000500.00");
        body.put("origDate", "2024-06-15");
        body.put("procDate", "2024-06-16");
        body.put("merchantId", "123456789");
        body.put("merchantName", "Test Merchant");
        body.put("merchantCity", "New York");
        body.put("merchantZip", "10001");
        body.put("confirm", "Y");
        return body;
    }

    // ========================================================================
    // POST /api/transactions — Full submit (ENTER + confirm=Y)
    // ========================================================================
    @Nested
    @DisplayName("POST /api/transactions")
    class AddTransaction {

        @Test
        @DisplayName("IT-01: Successful add — 201 with tran ID")
        void successfulAdd() throws Exception {
            Map<String, Object> body = makeValidBody();
            mockMvc.perform(post("/api/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.tranId").isNotEmpty())
                    .andExpect(jsonPath("$.message").value(containsString("Transaction added successfully")));
        }

        @Test
        @DisplayName("IT-02: Validation error — 422 with error message")
        void validationError() throws Exception {
            Map<String, Object> body = makeValidBody();
            body.put("acctId", "ABC");
            mockMvc.perform(post("/api/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Account ID must be Numeric..."))
                    .andExpect(jsonPath("$.errorField").value("acctId"));
        }

        @Test
        @DisplayName("IT-03: Neither key entered — 422")
        void neitherKeyEntered() throws Exception {
            Map<String, Object> body = makeValidBody();
            body.remove("acctId");
            body.remove("cardNum");
            mockMvc.perform(post("/api/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value("Account or Card Number must be entered..."));
        }

        @Test
        @DisplayName("IT-04: Empty typeCd — 422 (first-error-only)")
        void emptyTypeCd() throws Exception {
            Map<String, Object> body = makeValidBody();
            body.put("typeCd", "");
            mockMvc.perform(post("/api/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value("Type CD can NOT be empty..."))
                    .andExpect(jsonPath("$.errorField").value("typeCd"));
        }

        @Test
        @DisplayName("IT-05: Bad amount format — 422")
        void badAmountFormat() throws Exception {
            Map<String, Object> body = makeValidBody();
            body.put("amount", "500.00");
            mockMvc.perform(post("/api/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value("Amount should be in format -99999999.99"));
        }

        @Test
        @DisplayName("IT-06: Invalid date (Feb 30) — 422")
        void invalidDate() throws Exception {
            Map<String, Object> body = makeValidBody();
            body.put("origDate", "2024-02-30");
            mockMvc.perform(post("/api/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value("Orig Date - Not a valid date..."));
        }

        @Test
        @DisplayName("IT-07: Confirm = N — 422 (ask again)")
        void confirmNo() throws Exception {
            Map<String, Object> body = makeValidBody();
            body.put("confirm", "N");
            mockMvc.perform(post("/api/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value("Confirm to add this transaction..."));
        }

        @Test
        @DisplayName("IT-08: Card number lookup — 201")
        void cardNumberLookup() throws Exception {
            Map<String, Object> body = makeValidBody();
            body.remove("acctId");
            body.put("cardNum", "4111111111111111");
            mockMvc.perform(post("/api/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.tranId").isNotEmpty());
        }
    }

    // ========================================================================
    // POST /api/transactions/validate — Validate only (ENTER without confirm)
    // ========================================================================
    @Nested
    @DisplayName("POST /api/transactions/validate")
    class ValidateTransaction {

        @Test
        @DisplayName("IT-09: Valid input — 200 with resolved values")
        void validInput() throws Exception {
            Map<String, Object> body = makeValidBody();
            body.remove("confirm");
            mockMvc.perform(post("/api/transactions/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.resolvedCardNum").value("4111111111111111"))
                    .andExpect(jsonPath("$.resolvedAcctId").value("00000000001"))
                    .andExpect(jsonPath("$.normalizedAmount").value("+00000500.00"))
                    .andExpect(jsonPath("$.message").value("Confirm to add this transaction..."));
        }

        @Test
        @DisplayName("IT-10: Validation error — 422")
        void validationError() throws Exception {
            Map<String, Object> body = makeValidBody();
            body.put("amount", "INVALID");
            body.remove("confirm");
            mockMvc.perform(post("/api/transactions/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ========================================================================
    // GET /api/transactions/last — PF5 (Copy Last Transaction)
    // ========================================================================
    @Nested
    @DisplayName("GET /api/transactions/last")
    class CopyLastTransaction {

        @Test
        @DisplayName("IT-11: Copy last with valid acctId — 200 with all fields")
        void copyLastWithAcctId() throws Exception {
            mockMvc.perform(get("/api/transactions/last")
                            .param("acctId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.typeCd").isNotEmpty())
                    .andExpect(jsonPath("$.catCd").isNotEmpty())
                    .andExpect(jsonPath("$.source").isNotEmpty())
                    .andExpect(jsonPath("$.amount").isNotEmpty());
        }

        @Test
        @DisplayName("IT-12: Copy last without key — 422")
        void copyLastWithoutKey() throws Exception {
            mockMvc.perform(get("/api/transactions/last"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value("Account or Card Number must be entered..."));
        }

        @Test
        @DisplayName("IT-13: Copy last with invalid acctId — 422")
        void copyLastInvalidAcctId() throws Exception {
            mockMvc.perform(get("/api/transactions/last")
                            .param("acctId", "ABC"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value("Account ID must be Numeric..."));
        }
    }
}
