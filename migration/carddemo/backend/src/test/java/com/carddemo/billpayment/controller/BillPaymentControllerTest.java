package com.carddemo.billpayment.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The CB00 endpoint: every COBIL00C ENTER turn over HTTP, with the verbatim
 * ERRMSG text in the body and the status the turn maps to.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BillPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private org.springframework.test.web.servlet.ResultActions enter(String accountId, String confirm)
            throws Exception {
        String body = String.format("{\"accountId\":%s,\"confirm\":%s}", json(accountId), json(confirm));
        return mockMvc.perform(post("/api/billpay/screen")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    @Test
    void frBp24_balanceInquiryOverHttp() throws Exception {
        enter("1", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("1"))
                .andExpect(jsonPath("$.currentBalance").value("+0000000194.00"))
                .andExpect(jsonPath("$.message").value("Confirm to make a bill payment..."))
                .andExpect(jsonPath("$.cursor").value("CONFIRM"))
                .andExpect(jsonPath("$.error").value(false));
    }

    @Test
    void frBp10_emptyAccountIdIsBadRequest() throws Exception {
        enter("", "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Acct ID can NOT be empty..."));
    }

    @Test
    void frBp13_invalidConfirmIsBadRequest() throws Exception {
        enter("1", "Q").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value. Valid values are (Y/N)..."));
    }

    @Test
    void frBp21_unknownAccountIsNotFound() throws Exception {
        enter("99999999999", "").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account ID NOT found..."));
    }

    @Test
    void frBp12_declineClearsTheScreen() throws Exception {
        enter("1", "N").andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(""))
                .andExpect(jsonPath("$.currentBalance").value(""))
                .andExpect(jsonPath("$.confirm").value(""))
                .andExpect(jsonPath("$.message").value(""))
                .andExpect(jsonPath("$.tranId").doesNotExist());
    }

    @Test
    void frBp39_successMessageOverHttp() throws Exception {
        enter("1", "Y").andExpect(status().isOk())
                .andExpect(jsonPath("$.tranId").value("0000000000000031"))
                .andExpect(jsonPath("$.message")
                        .value("Payment successful.  Your Transaction ID is 0000000000000031."))
                .andExpect(jsonPath("$.messageColour").value("GREEN"));
    }
}
