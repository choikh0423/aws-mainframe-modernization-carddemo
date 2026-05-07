package com.carddemo.transaction.parity;

import com.carddemo.transaction.TransactionAddApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
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
 * Parity tests: side-by-side behavioral comparison between
 * COBOL COTRN02C and Java TransactionAddController.
 *
 * Each test documents the exact COBOL behavior (paragraph + line numbers)
 * and verifies the Java implementation produces identical output.
 *
 * Test naming: PARITY-{paragraph}-{scenario}
 */
@SpringBootTest(classes = TransactionAddApplication.class)
@AutoConfigureMockMvc
class COTRN02CParityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Map<String, Object> makeBody(Map<String, String> overrides) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("acctId", "1");
        body.put("typeCd", "01");
        body.put("catCd", "5001");
        body.put("source", "MANUAL");
        body.put("description", "Parity test");
        body.put("amount", "+00000100.00");
        body.put("origDate", "2024-01-15");
        body.put("procDate", "2024-01-16");
        body.put("merchantId", "123456789");
        body.put("merchantName", "Parity Merchant");
        body.put("merchantCity", "Chicago");
        body.put("merchantZip", "60601");
        body.put("confirm", "Y");
        if (overrides != null) {
            body.putAll(overrides);
        }
        return body;
    }

    // ========================================================================
    // MAIN-PARA parity
    // ========================================================================

    @Test
    @DisplayName("PARITY-MAIN-PARA: ENTER with valid data -> ADD-TRANSACTION")
    void mainPara_enterValid() throws Exception {
        // COBOL: EIBAID = DFHENTER -> PROCESS-ENTER-KEY -> ADD-TRANSACTION
        // Java:  POST /api/transactions with confirm=Y -> 201
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeBody(null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(
                        containsString("Transaction added successfully")));
    }

    // ========================================================================
    // VALIDATE-INPUT-KEY-FIELDS parity (lines 193-230)
    // ========================================================================

    @Test
    @DisplayName("PARITY-KEY: acctId entered + non-numeric -> exact error")
    void keyFields_acctIdNonNumeric() throws Exception {
        // COBOL line 199: WHEN WS-ACCT-ID-N IS NOT NUMERIC
        // MOVE 'Account ID must be Numeric...' TO WS-MESSAGE
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("acctId", "ABCDE");
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeBody(overrides))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Account ID must be Numeric..."))
                .andExpect(jsonPath("$.errorField").value("acctId"));
    }

    @Test
    @DisplayName("PARITY-KEY: acctId entered + not in XREF -> exact error")
    void keyFields_acctIdNotFound() throws Exception {
        // COBOL line 206: DFHRESP(NOTFND) on CXACAIX
        // MOVE 'Account ID NOT found...' TO WS-MESSAGE
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("acctId", "99999999999");
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeBody(overrides))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Account ID NOT found..."));
    }

    @Test
    @DisplayName("PARITY-KEY: cardNum entered + non-numeric -> exact error")
    void keyFields_cardNumNonNumeric() throws Exception {
        // COBOL line 217: WHEN WS-CARD-NUM-N IS NOT NUMERIC
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("acctId", "");
        overrides.put("cardNum", "XXXXXXXXXX");
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeBody(overrides))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Card Number must be Numeric..."))
                .andExpect(jsonPath("$.errorField").value("cardNum"));
    }

    @Test
    @DisplayName("PARITY-KEY: neither entered -> exact error")
    void keyFields_neitherEntered() throws Exception {
        // COBOL line 227: WHEN OTHER
        // MOVE 'Account or Card Number must be entered...' TO WS-MESSAGE
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("acctId", "");
        overrides.put("cardNum", "");
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeBody(overrides))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        "Account or Card Number must be entered..."));
    }

    // ========================================================================
    // VALIDATE-INPUT-DATA-FIELDS parity (lines 235-437)
    // ========================================================================

    @Test
    @DisplayName("PARITY-DATA: amount format validation -> exact COBOL format check")
    void dataFields_amountFormat() throws Exception {
        // COBOL lines 339-351: character-by-character validation
        // Position 1: +/-, Position 2-9: digits, Position 10: '.', Position 11-12: digits
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("amount", "500.00");
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeBody(overrides))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        "Amount should be in format -99999999.99"));
    }

    @Test
    @DisplayName("PARITY-DATA: origDate YYYY-MM-DD format check")
    void dataFields_origDateFormat() throws Exception {
        // COBOL lines 353-366: position-by-position format check
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("origDate", "01/15/2024");
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeBody(overrides))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        "Orig Date should be in format YYYY-MM-DD"));
    }

    @Test
    @DisplayName("PARITY-DATA: date validity via CSUTLDTC replacement")
    void dataFields_dateValidity() throws Exception {
        // COBOL lines 389-407: CALL CSUTLDTC -> CEEDAYS
        // Feb 30 is invalid in both CEEDAYS and java.time
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("origDate", "2024-02-30");
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeBody(overrides))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        "Orig Date - Not a valid date..."));
    }

    // ========================================================================
    // PROCESS-ENTER-KEY parity (confirmation, lines 169-188)
    // ========================================================================

    @Test
    @DisplayName("PARITY-CONFIRM: confirm=N -> 'Confirm to add...' (exact text)")
    void confirm_no() throws Exception {
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("confirm", "N");
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeBody(overrides))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        "Confirm to add this transaction..."));
    }

    @Test
    @DisplayName("PARITY-CONFIRM: confirm=X -> 'Invalid value...' (exact text)")
    void confirm_invalid() throws Exception {
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("confirm", "X");
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeBody(overrides))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        "Invalid value. Valid values are (Y/N)..."));
    }

    // ========================================================================
    // COPY-LAST-TRAN-DATA parity (lines 471-495)
    // ========================================================================

    @Test
    @DisplayName("PARITY-COPY-LAST: PF5 copies all 11 fields from absolute last record")
    void copyLast_allFields() throws Exception {
        // COBOL: STARTBR at HIGH-VALUES, READPREV -> last record in VSAM
        // Java:  findTopByOrderByTranIdDesc() -> last record by tran_id
        mockMvc.perform(get("/api/transactions/last")
                        .param("acctId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.typeCd").isNotEmpty())
                .andExpect(jsonPath("$.catCd").isNotEmpty())
                .andExpect(jsonPath("$.source").isNotEmpty())
                .andExpect(jsonPath("$.description").isNotEmpty())
                .andExpect(jsonPath("$.amount").isNotEmpty())
                .andExpect(jsonPath("$.origDate").isNotEmpty())
                .andExpect(jsonPath("$.procDate").isNotEmpty())
                .andExpect(jsonPath("$.merchantId").isNotEmpty())
                .andExpect(jsonPath("$.merchantName").isNotEmpty())
                .andExpect(jsonPath("$.merchantCity").isNotEmpty())
                .andExpect(jsonPath("$.merchantZip").isNotEmpty());
    }

    // ========================================================================
    // Short-circuit parity: COBOL RETURN in SEND-TRNADD-SCREEN
    // ========================================================================

    @Test
    @DisplayName("PARITY-SHORT-CIRCUIT: Multiple errors — only first returned")
    void shortCircuit_firstErrorOnly() throws Exception {
        // COBOL: SEND-TRNADD-SCREEN contains EXEC CICS RETURN
        // This means after first error is shown, the task terminates.
        // Java must replicate: throw ValidationException on first error.
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("typeCd", "");     // empty — first error
        overrides.put("catCd", "");      // empty — second error (should NOT appear)
        overrides.put("source", "");     // empty — third error (should NOT appear)
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeBody(overrides))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Type CD can NOT be empty..."))
                .andExpect(jsonPath("$.errorField").value("typeCd"));
    }
}
