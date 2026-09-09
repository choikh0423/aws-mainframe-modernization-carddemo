package com.carddemo.authshell;

import com.carddemo.common.message.CardDemoMessages;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static com.carddemo.authshell.AuthControllerIntegrationTest.signOn;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CM00 and CA00 end to end: the map text each menu paints, the commarea it
 * hands to the program it transfers to, and the two ways out — PF3 and an
 * unsupported AID.
 */
@SpringBootTest
class MenuControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    private static String selection(String option) {
        return "{\"option\":\"%s\"}".formatted(option);
    }

    /** FR-MEN-01, FR-MEN-02, FR-TXT-01, FR-TXT-02 */
    @Test
    void mainMenuScreenServesTheMapLiterals() throws Exception {
        MockMvc mockMvc = mockMvc();
        MockHttpSession session = signOn(mockMvc, "USER0001", "PASSWORD");

        mockMvc.perform(get("/api/menu/main").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tranId").value("CM00"))
                .andExpect(jsonPath("$.programName").value("COMEN01C"))
                .andExpect(jsonPath("$.title01").value(ScreenText.TITLE01))
                .andExpect(jsonPath("$.title02").value(ScreenText.TITLE02))
                .andExpect(jsonPath("$.title").value(ScreenText.MAIN_MENU_TITLE))
                .andExpect(jsonPath("$.prompt").value(ScreenText.MENU_PROMPT))
                .andExpect(jsonPath("$.optionLength").value(2))
                .andExpect(jsonPath("$.pfKeys").value(ScreenText.MENU_PF_KEYS))
                .andExpect(jsonPath("$.options.length()").value(11))
                .andExpect(jsonPath("$.options[0].displayText")
                        .value(MenuCatalog.MAIN_MENU.get(0).displayText()));
    }

    /** FR-ADM-01, FR-ADM-02 */
    @Test
    void adminMenuScreenServesTheMapLiterals() throws Exception {
        MockMvc mockMvc = mockMvc();
        MockHttpSession session = signOn(mockMvc, "ADMIN001", "PASSWORD");

        mockMvc.perform(get("/api/admin/menu").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tranId").value("CA00"))
                .andExpect(jsonPath("$.programName").value("COADM01C"))
                .andExpect(jsonPath("$.title").value(ScreenText.ADMIN_MENU_TITLE))
                .andExpect(jsonPath("$.prompt").value(ScreenText.MENU_PROMPT))
                .andExpect(jsonPath("$.pfKeys").value(ScreenText.MENU_PF_KEYS))
                .andExpect(jsonPath("$.options.length()").value(6))
                .andExpect(jsonPath("$.options[0].displayText")
                        .value(MenuCatalog.ADMIN_MENU.get(0).displayText()));
    }

    /** FR-MEN-04 */
    @Test
    void mainMenuWithoutACommareaIsRefused() throws Exception {
        mockMvc().perform(get("/api/menu/main")).andExpect(status().isUnauthorized());
    }

    /**
     * FR-ADM-04. CA00 never reaches the controller without a commarea: the
     * shared /api/admin/** rule turns it away first, which is the migrated form
     * of "CICS never started this transaction for you".
     */
    @Test
    void adminMenuWithoutACommareaIsRefused() throws Exception {
        mockMvc().perform(get("/api/admin/menu")).andExpect(status().isForbidden());
    }

    /** FR-MEN-05, FR-NAV-04 */
    @Test
    void paintingTheMainMenuSetsPgmReenter() throws Exception {
        MockMvc mockMvc = mockMvc();
        MockHttpSession session = signOn(mockMvc, "USER0001", "PASSWORD");

        mockMvc.perform(get("/api/menu/main").session(session)).andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pgmContext").value(1));
    }

    /** FR-ADM-05 */
    @Test
    void paintingTheAdminMenuSetsPgmReenter() throws Exception {
        MockMvc mockMvc = mockMvc();
        MockHttpSession session = signOn(mockMvc, "ADMIN001", "PASSWORD");

        mockMvc.perform(get("/api/admin/menu").session(session)).andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pgmContext").value(1));
    }

    /** FR-MEN-12, FR-NAV-01, FR-NAV-03, FR-NAV-04, FR-NAV-06 */
    @Test
    void selectingAnOptionHandsTheCommareaToTheTargetProgram() throws Exception {
        MockMvc mockMvc = mockMvc();
        MockHttpSession session = signOn(mockMvc, "USER0001", "PASSWORD");
        mockMvc.perform(get("/api/menu/main").session(session)).andExpect(status().isOk());

        mockMvc.perform(post("/api/menu/main/select")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selection("6")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.optionNumber").value(6))
                .andExpect(jsonPath("$.optionName").value("Transaction List"))
                .andExpect(jsonPath("$.programName").value("COTRN00C"))
                .andExpect(jsonPath("$.optionEcho").value("06"));

        mockMvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromTranId").value("CM00"))
                .andExpect(jsonPath("$.fromProgram").value("COMEN01C"))
                .andExpect(jsonPath("$.nextProgram").value("COTRN00C"))
                .andExpect(jsonPath("$.pgmContext").value(0));
    }

    /** FR-ADM-09, FR-NAV-03 */
    @Test
    void selectingAnAdminOptionHandsTheCommareaToTheTargetProgram() throws Exception {
        MockMvc mockMvc = mockMvc();
        MockHttpSession session = signOn(mockMvc, "ADMIN001", "PASSWORD");

        mockMvc.perform(post("/api/admin/menu/select")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selection("1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.optionName").value("User List (Security)"))
                .andExpect(jsonPath("$.programName").value("COUSR00C"));

        mockMvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromTranId").value("CA00"))
                .andExpect(jsonPath("$.fromProgram").value("COADM01C"))
                .andExpect(jsonPath("$.nextProgram").value("COUSR00C"))
                .andExpect(jsonPath("$.pgmContext").value(0));
    }

    /** FR-NAV-02 */
    @Test
    void theMenusNeverRewriteTheSignedOnUser() throws Exception {
        MockMvc mockMvc = mockMvc();
        MockHttpSession session = signOn(mockMvc, "USER0001", "PASSWORD");

        mockMvc.perform(get("/api/menu/main").session(session)).andExpect(status().isOk());
        mockMvc.perform(post("/api/menu/main/select")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selection("1")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("USER0001"))
                .andExpect(jsonPath("$.userType").value("U"));
    }

    /** FR-MEN-07 */
    @Test
    void mainMenuRejectsAnInvalidOptionWithoutLosingTheSession() throws Exception {
        MockMvc mockMvc = mockMvc();
        MockHttpSession session = signOn(mockMvc, "USER0001", "PASSWORD");

        mockMvc.perform(post("/api/menu/main/select")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selection("99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.message").value(CardDemoMessages.MENU_INVALID_OPTION))
                .andExpect(jsonPath("$.messageColour").value("RED"));

        mockMvc.perform(get("/api/auth/session").session(session)).andExpect(status().isOk());
    }

    /** FR-MEN-13, FR-NAV-05 */
    @Test
    void pf3ReturnsToTheSignOnScreenWithoutACommarea() throws Exception {
        MockMvc mockMvc = mockMvc();
        MockHttpSession session = signOn(mockMvc, "USER0001", "PASSWORD");

        mockMvc.perform(post("/api/menu/main/select")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"option\":\"\",\"aid\":\"PF3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programName").value("COSGN00C"));

        mockMvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isUnauthorized());
    }

    /** FR-ADM-12 */
    @Test
    void pf3ReturnsToTheSignOnScreenFromTheAdminMenu() throws Exception {
        MockMvc mockMvc = mockMvc();
        MockHttpSession session = signOn(mockMvc, "ADMIN001", "PASSWORD");

        mockMvc.perform(post("/api/admin/menu/select")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"option\":\"\",\"aid\":\"PF3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programName").value("COSGN00C"));

        mockMvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isUnauthorized());
    }

    /** FR-MEN-14 */
    @Test
    void anInvalidKeyRedisplaysTheMainMenuMessage() throws Exception {
        MockMvc mockMvc = mockMvc();
        MockHttpSession session = signOn(mockMvc, "USER0001", "PASSWORD");

        mockMvc.perform(post("/api/menu/main/select")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"option\":\"1\",\"aid\":\"PF9\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.message").value(CardDemoMessages.INVALID_KEY));
    }

    /** FR-ADM-13 */
    @Test
    void anInvalidKeyRedisplaysTheAdminMenuMessage() throws Exception {
        MockMvc mockMvc = mockMvc();
        MockHttpSession session = signOn(mockMvc, "ADMIN001", "PASSWORD");

        mockMvc.perform(post("/api/admin/menu/select")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"option\":\"1\",\"aid\":\"PF9\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.message").value(CardDemoMessages.INVALID_KEY));
    }

    /**
     * FR-ADM-08 (migration boundary): COADM01C has no user-type check of its
     * own, so the shared /api/admin/** rule is what keeps a 'U' user out of
     * CA00 — the migrated equivalent of "COSGN00C never routed you here".
     */
    @Test
    void theAdminMenuIsUnreachableForAnOrdinaryUser() throws Exception {
        MockMvc mockMvc = mockMvc();
        MockHttpSession session = signOn(mockMvc, "USER0001", "PASSWORD");

        mockMvc.perform(get("/api/admin/menu").session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/menu/select")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selection("1")))
                .andExpect(status().isForbidden());
    }
}
