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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CC00 end to end: the map text COSGN00A shows, the USRSEC read behind ENTER,
 * the commarea the successful sign-on builds, and the PF3 that throws it away.
 * Each test drives one pseudo-conversation through its own
 * {@link MockHttpSession}, which is where the commarea lives.
 */
@SpringBootTest
class AuthControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    static String signOnBody(String userId, String password) {
        return "{\"userId\":\"%s\",\"password\":\"%s\"}".formatted(userId, password);
    }

    static MockHttpSession signOn(MockMvc mockMvc, String userId, String password)
            throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/signon")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signOnBody(userId, password)))
                .andExpect(status().isOk());
        return session;
    }

    /** FR-SGN-01, FR-TXT-01 */
    @Test
    void signOnScreenServesTheMapLiterals() throws Exception {
        mockMvc().perform(get("/api/auth/screen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banner").value(ScreenText.SIGNON_BANNER))
                .andExpect(jsonPath("$.prompt").value(ScreenText.SIGNON_PROMPT))
                .andExpect(jsonPath("$.userIdLabel").value(ScreenText.SIGNON_USER_ID_LABEL))
                .andExpect(jsonPath("$.passwordLabel").value(ScreenText.SIGNON_PASSWORD_LABEL))
                .andExpect(jsonPath("$.fieldHint").value(ScreenText.SIGNON_FIELD_HINT))
                .andExpect(jsonPath("$.fieldLength").value(8))
                .andExpect(jsonPath("$.pfKeys").value(ScreenText.SIGNON_PF_KEYS))
                .andExpect(jsonPath("$.art.length()").value(9));
    }

    /** FR-SGN-17, FR-TXT-02 */
    @Test
    void signOnScreenServesTheHeaderFields() throws Exception {
        mockMvc().perform(get("/api/auth/screen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tranId").value("CC00"))
                .andExpect(jsonPath("$.programName").value("COSGN00C"))
                .andExpect(jsonPath("$.title01").value(ScreenText.TITLE01))
                .andExpect(jsonPath("$.title02").value(ScreenText.TITLE02))
                .andExpect(jsonPath("$.applidLabel").value(ScreenText.SIGNON_APPLID_LABEL))
                .andExpect(jsonPath("$.sysidLabel").value(ScreenText.SIGNON_SYSID_LABEL));
    }

    /** FR-SGN-02 */
    @Test
    void sessionIsUnauthorizedBeforeSignOn() throws Exception {
        mockMvc().perform(get("/api/auth/session")).andExpect(status().isUnauthorized());
    }

    /** FR-SGN-08, FR-SGN-13, FR-NAV-01, FR-NAV-03 */
    @Test
    void administratorSignsOnAndIsSentToTheAdminMenu() throws Exception {
        mockMvc().perform(post("/api/auth/signon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signOnBody("ADMIN001", "PASSWORD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("ADMIN001"))
                .andExpect(jsonPath("$.userType").value("A"))
                .andExpect(jsonPath("$.fromTranId").value("CC00"))
                .andExpect(jsonPath("$.fromProgram").value("COSGN00C"))
                .andExpect(jsonPath("$.pgmContext").value(0))
                .andExpect(jsonPath("$.nextProgram").value("COADM01C"));
    }

    /** FR-SGN-14 */
    @Test
    void rejectedSignOnLeavesNoSession() throws Exception {
        MockMvc mockMvc = mockMvc();
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/auth/signon")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signOnBody("USER0001", "NOTRIGHT")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(CardDemoMessages.SIGNON_WRONG_PASSWORD))
                .andExpect(jsonPath("$.errorField").value("password"));

        mockMvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isUnauthorized());
    }

    /** FR-SGN-16 */
    @Test
    void invalidKeyRedisplaysTheSignOnMessage() throws Exception {
        mockMvc().perform(post("/api/auth/signon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"USER0001\",\"password\":\"PASSWORD\",\"aid\":\"PF9\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(CardDemoMessages.INVALID_KEY));
    }

    /** FR-SGN-13, FR-NAV-01 */
    @Test
    void signOnStoresTheCommareaForTheNextScreen() throws Exception {
        MockMvc mockMvc = mockMvc();
        MockHttpSession session = signOn(mockMvc, "USER0001", "PASSWORD");

        mockMvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("USER0001"))
                .andExpect(jsonPath("$.userType").value("U"))
                .andExpect(jsonPath("$.nextProgram").value("COMEN01C"));
    }

    /** FR-SGN-15, FR-NAV-05 */
    @Test
    void signOffAnswersWithTheThankYouTextAndDropsTheSession() throws Exception {
        MockMvc mockMvc = mockMvc();
        MockHttpSession session = signOn(mockMvc, "USER0001", "PASSWORD");

        mockMvc.perform(post("/api/auth/signoff").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(CardDemoMessages.THANK_YOU));

        mockMvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isUnauthorized());
    }
}
