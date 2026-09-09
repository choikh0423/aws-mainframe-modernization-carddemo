package com.carddemo.pendingauth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level coverage of the CPVS/CPVD endpoints against the seeded DBPAUTP0
 * demo hierarchy (account 1 has seven children, account 2 one).
 *
 * <p>Like the reference stream's controller tests, MockMvc is built from the
 * autowired context so this reuses the cached default {@code @SpringBootTest}
 * context. Every one of these screens answers a bad input with HTTP 200 and a
 * message line, because that is what the 3270 program does.
 */
@SpringBootTest
class PendingAuthControllerTest {

    /** Account 2's only authorization: PAUTH0000000008. */
    private static final String ACCT_2_KEY = "74984879999999";
    /** Account 1's fifth authorization, PAUTH0000000005 — this test's fraud subject. */
    private static final String FRAUD_KEY = "74984889999999";

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void listWithoutAnAccountIdReturns200AndTheLegacyPrompt() throws Exception {
        mockMvc.perform(get("/api/pending-authorizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Please enter Acct Id..."))
                .andExpect(jsonPath("$.rows.length()").value(0));
    }

    @Test
    void listReturnsTheHeaderAndTheFirstFiveRows() throws Exception {
        mockMvc.perform(get("/api/pending-authorizations").param("acctId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acctId").value("00000000001"))
                .andExpect(jsonPath("$.custId").value("000000001"))
                .andExpect(jsonPath("$.approvedCount").value("006"))
                .andExpect(jsonPath("$.rows.length()").value(5))
                .andExpect(jsonPath("$.rows[0].authKey").value("74984849999999"))
                .andExpect(jsonPath("$.rows[0].transactionId").value("PAUTH0000000001"))
                .andExpect(jsonPath("$.rows[0].amountDisplay").value("       13.75"))
                .andExpect(jsonPath("$.pageNum").value(1))
                .andExpect(jsonPath("$.nextPage").value(true));
    }

    @Test
    void listPagesForwardAndBackWithTheKeysTheScreenCarries() throws Exception {
        mockMvc.perform(get("/api/pending-authorizations")
                        .param("acctId", "1")
                        .param("dir", "next")
                        .param("startKey", FRAUD_KEY)
                        .param("pageNum", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0].transactionId").value("PAUTH0000000006"))
                .andExpect(jsonPath("$.nextPage").value(false))
                .andExpect(jsonPath("$.pageNum").value(2));

        mockMvc.perform(get("/api/pending-authorizations")
                        .param("acctId", "1")
                        .param("dir", "prev")
                        .param("pageNum", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("You are already at the top of the page..."));
    }

    @Test
    void selectionTransfersToCpvdOnlyForS() throws Exception {
        mockMvc.perform(post("/api/pending-authorizations/selection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"acctId":"1","rows":[
                                  {"flag":" ","authKey":"74984849999999"},
                                  {"flag":"S","authKey":"74984859999999"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selected").value(true))
                .andExpect(jsonPath("$.nextProgram").value("COPAUS1C"))
                .andExpect(jsonPath("$.nextTranId").value("CPVD"))
                .andExpect(jsonPath("$.authKey").value("74984859999999"));

        mockMvc.perform(post("/api/pending-authorizations/selection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"acctId":"1","rows":[
                                  {"flag":"X","authKey":"74984849999999"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selected").value(false))
                .andExpect(jsonPath("$.message").value("Invalid selection. Valid value is S"));
    }

    @Test
    void detailReturnsTheAuthorizationAndABlankAreaForAnUnknownKey() throws Exception {
        mockMvc.perform(get("/api/pending-authorizations/detail")
                        .param("acctId", "1")
                        .param("authKey", "74984849999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.authKey").value("74984849999999"))
                .andExpect(jsonPath("$.cardNumber").value("9680294154603697"))
                .andExpect(jsonPath("$.authDate").value("01/15/25"))
                .andExpect(jsonPath("$.authTime").value("15:00:00"))
                .andExpect(jsonPath("$.transactionId").value("PAUTH0000000001"));

        mockMvc.perform(get("/api/pending-authorizations/detail")
                        .param("acctId", "1")
                        .param("authKey", "NOTAKEY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false))
                .andExpect(jsonPath("$.cardNumber").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void detailNextStopsAtTheLastAuthorizationOfTheAccount() throws Exception {
        mockMvc.perform(get("/api/pending-authorizations/detail/next")
                        .param("acctId", "1")
                        .param("authKey", "74984849999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("PAUTH0000000002"));

        mockMvc.perform(get("/api/pending-authorizations/detail/next")
                        .param("acctId", "2")
                        .param("authKey", ACCT_2_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("PAUTH0000000008"))
                .andExpect(jsonPath("$.message")
                        .value("Already at the last Authorization..."));
    }

    @Test
    void fraudMarksThenRemovesTheFlagOnTheSelectedAuthorization() throws Exception {
        mockMvc.perform(post("/api/pending-authorizations/detail/fraud")
                        .param("acctId", "1")
                        .param("authKey", FRAUD_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("AUTH MARKED FRAUD..."))
                .andExpect(jsonPath("$.fraud").value(org.hamcrest.Matchers.startsWith("F-")));

        mockMvc.perform(post("/api/pending-authorizations/detail/fraud")
                        .param("acctId", "1")
                        .param("authKey", FRAUD_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("AUTH FRAUD REMOVED..."))
                .andExpect(jsonPath("$.fraud").value(org.hamcrest.Matchers.startsWith("R-")));
    }
}
