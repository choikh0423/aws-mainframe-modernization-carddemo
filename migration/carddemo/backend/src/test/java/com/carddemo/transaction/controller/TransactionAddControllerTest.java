package com.carddemo.transaction.controller;

import com.carddemo.transaction.dto.TransactionAddRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level FR-A1..FR-A11 coverage for {@link TransactionAddController} against
 * the seeded H2 DB. Confirms status codes + the exact legacy ERRMSG / success
 * text. {@code @Transactional} rolls back the POST writes so the shared in-memory
 * DB stays at 30 rows for the rest of the suite.
 *
 * <p>MockMvc is built from the autowired context (not {@code @AutoConfigureMockMvc})
 * to keep the same cached {@code @SpringBootTest} context the rest of the suite uses.
 */
@SpringBootTest
@Transactional
class TransactionAddControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    private static TransactionAddRequest validRequest() {
        TransactionAddRequest r = new TransactionAddRequest();
        r.setAccountId("10000000001");
        r.setCardNumber("");
        r.setTypeCd("07");
        r.setCategoryCd("1234");
        r.setSource("POS");
        r.setDescription("NEW TRANSACTION");
        r.setAmount("-00000123.45");
        r.setOrigDate("2023-07-01");
        r.setProcDate("2023-07-02");
        r.setMerchantId("900000009");
        r.setMerchantName("NEW MERCHANT");
        r.setMerchantCity("DENVER");
        r.setMerchantZip("80202");
        r.setConfirm("Y");
        return r;
    }

    private String json(TransactionAddRequest r) throws Exception {
        return objectMapper.writeValueAsString(r);
    }

    @Test
    void frA6_validConfirmed_returns201WithGreenMessageAndTranId() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tranId").value("0000000000000031"))
                .andExpect(jsonPath("$.message")
                        .value("Transaction added successfully.  Your Tran ID is 0000000000000031."));
    }

    @Test
    void frA3_bothKeysEmpty_returns400() throws Exception {
        TransactionAddRequest r = validRequest();
        r.setAccountId("");
        r.setCardNumber("");
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(r)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account or Card Number must be entered..."));
    }

    @Test
    void frA4_accountNotFound_returns404() throws Exception {
        TransactionAddRequest r = validRequest();
        r.setAccountId("99999999999");
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(r)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account ID NOT found..."));
    }

    @Test
    void frA5_cardNotFound_returns404() throws Exception {
        TransactionAddRequest r = validRequest();
        r.setAccountId("");
        r.setCardNumber("1234567890123456");
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(r)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Card Number NOT found..."));
    }

    @Test
    void frA7_emptyField_returns400WithLegacyMessage() throws Exception {
        TransactionAddRequest r = validRequest();
        r.setDescription("");
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(r)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Description can NOT be empty..."));
    }

    @Test
    void frA9_badAmount_returns400() throws Exception {
        TransactionAddRequest r = validRequest();
        r.setAmount("123.45");
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(r)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Amount should be in format -99999999.99"));
    }

    @Test
    void frA11_confirmBlank_returns400() throws Exception {
        TransactionAddRequest r = validRequest();
        r.setConfirm("");
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(r)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Confirm to add this transaction..."));
    }

    @Test
    void frA1_resolveByAccount_returnsCard() throws Exception {
        mockMvc.perform(get("/api/cardxref/resolve").param("accountId", "10000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("10000000001"))
                .andExpect(jsonPath("$.cardNumber").value("4111111111111111"));
    }

    @Test
    void frA2_resolveByCard_returnsAccount() throws Exception {
        mockMvc.perform(get("/api/cardxref/resolve").param("cardNumber", "4222222222222222"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("10000000002"))
                .andExpect(jsonPath("$.cardNumber").value("4222222222222222"));
    }

    @Test
    void resolveAccountNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/cardxref/resolve").param("accountId", "99999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account ID NOT found..."));
    }

    @Test
    void frA12_latest_returnsMostRecentTransactionForCopyLast() throws Exception {
        // Literal /latest must beat the /{id} view mapping and return the max-key row (30).
        mockMvc.perform(get("/api/transactions/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("0000000000000030"));
    }
}
