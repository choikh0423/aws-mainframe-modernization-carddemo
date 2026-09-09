package com.carddemo.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.account.dto.AccountFields;
import com.carddemo.account.dto.AccountUpdateRequest;
import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CustomerRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.CustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Endpoint level parity for CAVW and CAUP against the seeded H2 estate
 * (db/seed, derived from app/data/ASCII): the view screen, the update fetch / validate / save
 * turns, and the failure paths (bad filter, unknown account, stale record).
 *
 * <p>{@code @Transactional} rolls the writes back so the seeded rows stay intact for the other
 * streams' tests sharing this context.
 */
@SpringBootTest
@Transactional
class AccountManagementIntegrationTest {

    private static final String ACCOUNT_ID = "00000000001";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    /** FR-AV-02, FR-AV-08..FR-AV-12. */
    @Test
    void viewShowsTheEditedAccountAndCustomerDetails() throws Exception {
        mockMvc().perform(get("/api/accounts/{accountId}", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.activeStatus").value("Y"))
                .andExpect(jsonPath("$.currBal").value("+        194.00"))
                .andExpect(jsonPath("$.creditLimit").value("+      2,020.00"))
                .andExpect(jsonPath("$.custId").value("000000001"))
                .andExpect(jsonPath("$.ssn").value("020-97-3888"))
                .andExpect(jsonPath("$.ficoScore").value("274"))
                .andExpect(jsonPath("$.infoMessage").value("Displaying details of given Account"));
    }

    /** FR-AV-03, FR-AV-04. */
    @Test
    void viewRejectsABlankOrNonNumericFilterWithTheLegacyText() throws Exception {
        mockMvc().perform(get("/api/accounts/{accountId}", "           "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No input received"));

        mockMvc().perform(get("/api/accounts/{accountId}", "0000000000X"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Account Filter must  be a non-zero 11 digit number"));
    }

    /** FR-AV-05: the xref read is first, so an unknown id reports the xref file. */
    @Test
    void viewReportsAnUnknownAccountAgainstTheCrossReferenceFile() throws Exception {
        mockMvc().perform(get("/api/accounts/{accountId}", "99999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Account:99999999999 not found in Cross ref file.  Resp:000000013  Reas:0000"));
    }

    /** FR-AU-05, FR-AU-06. */
    @Test
    void updateFetchReturnsTheSplitScreenFields() throws Exception {
        mockMvc().perform(get("/api/accounts/{accountId}/update", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SHOW_DETAILS"))
                .andExpect(jsonPath("$.infoMessage").value("Update account details presented above."))
                .andExpect(jsonPath("$.details.openYear").value("2014"))
                .andExpect(jsonPath("$.details.openMonth").value("11"))
                .andExpect(jsonPath("$.details.openDay").value("20"))
                .andExpect(jsonPath("$.details.ssnPart1").value("020"))
                .andExpect(jsonPath("$.details.phone1Area").value("908"));
    }

    /** FR-AU-07: ENTER with nothing changed. */
    @Test
    void validateReportsNoChangeWhenTheScreenIsUntouched() throws Exception {
        AccountFields original = fetchScreen();
        mockMvc().perform(post("/api/accounts/{accountId}/update/validate", ACCOUNT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(original, AccountFieldsFixture.copyOf(original))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SHOW_DETAILS"))
                .andExpect(jsonPath("$.errorMessage")
                        .value("No change detected with respect to values fetched."));
    }

    /** FR-AU-08, FR-AU-10: the first failing edit owns the error line. */
    @Test
    void validateReturnsTheFirstFailingEditThenAcceptsACleanScreen() throws Exception {
        AccountFields original = fetchScreen();

        AccountFields spoiled = AccountFieldsFixture.copyOf(original);
        spoiled.activeStatus = "X";
        spoiled.lastName = "Kessler1";
        mockMvc().perform(post("/api/accounts/{accountId}/update/validate", ACCOUNT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(original, spoiled)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CHANGES_NOT_OK"))
                .andExpect(jsonPath("$.errorMessage").value("Account Status must be Y or N."));

        mockMvc().perform(post("/api/accounts/{accountId}/update/validate", ACCOUNT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(original, changedScreen(original))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CHANGES_OK_NOT_CONFIRMED"))
                .andExpect(jsonPath("$.infoMessage").value("Changes validated.Press F5 to save"));
    }

    /** FR-AU-29, FR-AU-33, FR-AU-34, FR-AQ-08. */
    @Test
    void saveRewritesBothRecords() throws Exception {
        AccountFields original = fetchScreen();
        AccountFields updated = changedScreen(original);

        mockMvc().perform(post("/api/accounts/{accountId}/update/save", ACCOUNT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(original, updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CHANGES_OKAYED_AND_DONE"))
                .andExpect(jsonPath("$.infoMessage").value("Changes committed to database"));

        AccountRecord account = accountRepository.findById(1L).orElseThrow();
        CustomerRecord customer = customerRepository.findById(1L).orElseThrow();
        assertThat(account.getCreditLimit()).isEqualByComparingTo("3000.00");
        assertThat(customer.getFicoCreditScore()).isEqualTo(700);
        assertThat(customer.getAddrZip()).isEqualTo("27546");
        assertThat(customer.getPhoneNum1()).isEqualTo("(908)119-8310");
        // The account's own zip is wiped by the legacy INITIALIZE of ACCT-UPDATE-RECORD.
        assertThat(account.getAddrZip()).isEmpty();
    }

    /** FR-AU-32: saving an unchanged screen is refused with the same wording as ENTER. */
    @Test
    void saveRefusesAnUnchangedScreen() throws Exception {
        AccountFields original = fetchScreen();
        mockMvc().perform(post("/api/accounts/{accountId}/update/save", ACCOUNT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(original, AccountFieldsFixture.copyOf(original))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("No change detected with respect to values fetched."));
    }

    /** FR-AU-31: the fetched snapshot no longer matches the locked row. */
    @Test
    void saveRefusesAStaleSnapshot() throws Exception {
        AccountFields original = fetchScreen();
        AccountFields updated = changedScreen(original);
        original.creditLimit = "+      9,999.00";

        mockMvc().perform(post("/api/accounts/{accountId}/update/save", ACCOUNT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(original, updated)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Record changed by some one else. Please review"));
    }

    /** FR-AU-30: the edits run again on F5, so an invalid screen never reaches the files. */
    @Test
    void saveRunsTheEditsAgain() throws Exception {
        AccountFields original = fetchScreen();
        AccountFields updated = changedScreen(original);
        updated.ficoScore = "274";

        mockMvc().perform(post("/api/accounts/{accountId}/update/save", ACCOUNT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(original, updated)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("FICO Score: should be between 300 and 850"));
    }

    private AccountFields fetchScreen() throws Exception {
        String json = mockMvc().perform(get("/api/accounts/{accountId}/update", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.treeToValue(objectMapper.readTree(json).get("details"), AccountFields.class);
    }

    /**
     * The seeded row would not pass COACTUPC's own edits — its FICO score is below 300, its zip
     * does not belong to its state and its second phone number uses an area code that is not in
     * CSLKPCDY — so a saveable screen has to correct those first.
     */
    private AccountFields changedScreen(AccountFields original) {
        AccountFields updated = AccountFieldsFixture.copyOf(original);
        updated.creditLimit = "+      3,000.00";
        updated.ficoScore = "700";
        updated.zip = "27546";
        updated.state = "NC";
        updated.phone2Area = "704";
        return updated;
    }

    private String body(AccountFields original, AccountFields updated) throws Exception {
        AccountUpdateRequest request = new AccountUpdateRequest();
        request.original = original;
        request.updated = updated;
        return objectMapper.writeValueAsString(request);
    }
}
