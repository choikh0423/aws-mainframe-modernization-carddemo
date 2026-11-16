package com.carddemo.statements;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Statements API against the seeded H2 estate (account 00000000001, card 9680294154603697,
 * customer 1). Archive rows are inserted per test and rolled back by {@code @Transactional}.
 */
@SpringBootTest
@Transactional
class StatementControllerIntegrationTest {

    private static final String ACCOUNT_ID = "00000000001";
    private static final String CARD = "9680294154603697";
    private static final String HTML = "<!DOCTYPE html>\n<html lang=\"en\">\n<body style=\"margin:0px;\">"
            + "<p style=\"font-size:16px\">Bank of XYZ</p></body>\n</html>";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private StatementArchiveRepository repository;

    private MockMvc mockMvc;
    private MockHttpSession session;

    @BeforeEach
    void signOn() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/signon").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"USER0001\",\"password\":\"PASSWORD\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void listsAnAccountsStatementsNewestCycleFirstWithTheCardMasked() throws Exception {
        archive("2026-09", 2, "-12.50");
        StatementArchiveRecord newest = archive("2026-10", 3, "1234.56");

        mockMvc.perform(get("/api/statements").param("acctId", ACCOUNT_ID).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acctId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.statements.length()").value(2))
                .andExpect(jsonPath("$.statements[0].stmtId").value(newest.getStmtId()))
                .andExpect(jsonPath("$.statements[0].cycle").value("2026-10"))
                .andExpect(jsonPath("$.statements[0].cardNum").value("************3697"))
                .andExpect(jsonPath("$.statements[0].txnCount").value(3))
                .andExpect(jsonPath("$.statements[0].totalAmount").value(1234.56))
                .andExpect(jsonPath("$.statements[0].generatedTs").value("2026-11-01T02:30:00"))
                .andExpect(jsonPath("$.statements[1].cycle").value("2026-09"))
                .andExpect(jsonPath("$.statements[1].totalAmount").value(-12.5))
                .andExpect(content().string(not(containsString(CARD))))
                .andExpect(content().string(not(containsString("textBody"))));
    }

    @Test
    void pagesTenStatementsAtATime() throws Exception {
        for (int month = 1; month <= 12; month++) {
            archive("2026-%02d".formatted(month), month, "10.00");
        }

        mockMvc.perform(get("/api/statements").param("acctId", ACCOUNT_ID).param("page", "0")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statements.length()").value(10))
                .andExpect(jsonPath("$.statements[0].cycle").value("2026-12"))
                .andExpect(jsonPath("$.statements[9].cycle").value("2026-03"))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        mockMvc.perform(get("/api/statements").param("acctId", ACCOUNT_ID).param("page", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.statements.length()").value(2))
                .andExpect(jsonPath("$.statements[0].cycle").value("2026-02"))
                .andExpect(jsonPath("$.statements[1].cycle").value("2026-01"))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(true));

        mockMvc.perform(get("/api/statements").param("acctId", ACCOUNT_ID).param("page", "5")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statements.length()").value(0));

        mockMvc.perform(get("/api/statements").param("acctId", ACCOUNT_ID).param("page", "-1")
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(StatementMessages.INVALID_PAGE));
    }

    @Test
    void anAccountWithNoStatementsIsAnEmptyList() throws Exception {
        mockMvc.perform(get("/api/statements").param("acctId", "00000000002").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acctId").value("00000000002"))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.statements.length()").value(0));
    }

    @Test
    void rejectsABadAccountIdWithTheAccountViewText() throws Exception {
        for (String bad : new String[] {"0000000000X", "00000000000", "1", "000000000011"}) {
            mockMvc.perform(get("/api/statements").param("acctId", bad).session(session))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Account Filter must  be a non-zero 11 digit number"));
        }
        mockMvc.perform(get("/api/statements").param("acctId", "   ").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No input received"));
        mockMvc.perform(get("/api/statements").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No input received"));
    }

    @Test
    void unknownAccountIsNotFound() throws Exception {
        mockMvc.perform(get("/api/statements").param("acctId", "99999999999").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account 99999999999 not found"));
    }

    @Test
    void detailCarriesTheAccountAndCustomerHeader() throws Exception {
        StatementArchiveRecord statement = archive("2026-10", 3, "1234.56");

        mockMvc.perform(get("/api/statements/{id}", statement.getStmtId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stmtId").value(statement.getStmtId()))
                .andExpect(jsonPath("$.acctId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.cardNum").value("************3697"))
                .andExpect(jsonPath("$.cycle").value("2026-10"))
                .andExpect(jsonPath("$.txnCount").value(3))
                .andExpect(jsonPath("$.totalAmount").value(1234.56))
                .andExpect(jsonPath("$.account.activeStatus").value("Y"))
                .andExpect(jsonPath("$.account.currBal").value(194.0))
                .andExpect(jsonPath("$.account.creditLimit").value(2020.0))
                .andExpect(jsonPath("$.customer.custId").value("000000001"))
                .andExpect(jsonPath("$.customer.firstName").value("Immanuel"))
                .andExpect(jsonPath("$.customer.lastName").value("Kessler"))
                .andExpect(jsonPath("$.customer.addrLine1").value("618 Deshaun Route"))
                .andExpect(jsonPath("$.customer.state").value("NC"))
                .andExpect(jsonPath("$.customer.zip").value("12546"))
                .andExpect(content().string(not(containsString(CARD))))
                .andExpect(content().string(not(containsString("htmlBody"))));
    }

    @Test
    void htmlIsTheStoredBodyUnderAStrictContentSecurityPolicy() throws Exception {
        StatementArchiveRecord statement = archive("2026-10", 3, "1234.56");

        mockMvc.perform(get("/api/statements/{id}/html", statement.getStmtId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(HTML))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("script-src 'none'")))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("default-src 'none'")))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("frame-ancestors 'self'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void unknownStatementIdIsNotFound() throws Exception {
        long missing = repository.findAll().stream().mapToLong(StatementArchiveRecord::getStmtId)
                .max().orElse(0L) + 1000;

        mockMvc.perform(get("/api/statements/{id}", missing).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Statement " + missing + " not found"));
        mockMvc.perform(get("/api/statements/{id}/html", missing).session(session))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Statement " + missing + " not found"));
        mockMvc.perform(get("/api/statements/{id}", "abc").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(StatementMessages.INVALID_STATEMENT_ID));
    }

    @Test
    void everyEndpointNeedsASignedOnSession() throws Exception {
        StatementArchiveRecord statement = archive("2026-10", 3, "1234.56");

        mockMvc.perform(get("/api/statements").param("acctId", ACCOUNT_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(StatementMessages.SIGN_ON_REQUIRED));
        mockMvc.perform(get("/api/statements/{id}", statement.getStmtId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(StatementMessages.SIGN_ON_REQUIRED));
        mockMvc.perform(get("/api/statements/{id}/html", statement.getStmtId()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString("Bank of XYZ"))));

        mockMvc.perform(post("/api/auth/signoff").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/statements").param("acctId", ACCOUNT_ID).session(session))
                .andExpect(status().isUnauthorized());
    }

    private StatementArchiveRecord archive(String cycle, int txnCount, String total) {
        StatementArchiveRecord r = new StatementArchiveRecord();
        r.setAcctId(1L);
        r.setCardNum(CARD);
        r.setCycle(cycle);
        r.setGeneratedTs(LocalDateTime.of(2026, 11, 1, 2, 30, 0));
        r.setTxnCount(txnCount);
        r.setTotalAmount(new BigDecimal(total));
        r.setTextBody("text " + cycle);
        r.setHtmlBody(HTML);
        return repository.saveAndFlush(r);
    }
}
