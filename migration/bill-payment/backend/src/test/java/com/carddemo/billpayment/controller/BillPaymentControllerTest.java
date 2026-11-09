package com.carddemo.billpayment.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BillPaymentControllerTest {

    @Autowired MockMvc mvc;

    @BeforeEach
    void reset() throws Exception {
        mvc.perform(post("/api/demo/reset")).andExpect(status().isOk());
    }

    @Test
    void initialScreenHeader() throws Exception {
        mvc.perform(get("/api/bill-payment"))
                .andExpect(jsonPath("$.tranId").value("CB00"))
                .andExpect(jsonPath("$.programName").value("COBIL00C"))
                .andExpect(jsonPath("$.cursor").value("ACTIDIN"));
    }

    @Test
    void payThroughApiUpdatesAccount() throws Exception {
        mvc.perform(post("/api/bill-payment").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aid\":\"ENTER\",\"acctId\":\"00000000002\",\"confirm\":\"Y\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageColor").value("GREEN"))
                .andExpect(jsonPath("$.transactionId").value("0000000996722788"));
        mvc.perform(get("/api/demo/accounts/00000000002"))
                .andExpect(jsonPath("$.currentBalance").value("+0000000000.00"));
        mvc.perform(get("/api/demo/transactions/latest?limit=1"))
                .andExpect(jsonPath("$[0].tranId").value("0000000996722788"))
                .andExpect(jsonPath("$[0].amount").value(158.00));
    }
}
