package com.carddemo.card.controller;

import com.carddemo.card.repository.CardBrowseRepository;
import com.carddemo.common.domain.CardRecord;
import org.junit.jupiter.api.AfterEach;
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
 * HTTP-level coverage of the three S-03 endpoints against the seeded H2
 * database: the CCLI page and its selection POST, the CCDL read, and the CCUP
 * load / validate / save round trip, including the exact ERRMSG text on the
 * error paths.
 *
 * <p>MockMvc is built from the autowired {@link WebApplicationContext} so this
 * test reuses the cached default {@code @SpringBootTest} context, matching the
 * transaction stream's controller tests.
 */
@SpringBootTest
class CardManagementControllerTest {

    private static final String CARD = "8931369351894783";
    private static final String ACCOUNT = "00000000008";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private CardBrowseRepository repository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @AfterEach
    void restoreSeedRow() {
        CardRecord record = repository.findById(CARD).orElseThrow();
        record.setEmbossedName("Kelsie Dicki");
        record.setActiveStatus("Y");
        record.setExpiraionDate("2024-05-20");
        repository.saveAndFlush(record);
    }

    @Test
    void ccli_firstPageReturnsSevenRowsAndThePageState() throws Exception {
        mockMvc.perform(get("/api/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(7))
                .andExpect(jsonPath("$.pageNumber").value(1))
                .andExpect(jsonPath("$.rows[0].cardNum").value("0500024453765740"))
                .andExpect(jsonPath("$.rows[0].acctId").value("00000000050"))
                .andExpect(jsonPath("$.nextPageExists").value(true))
                .andExpect(jsonPath("$.infoMessage").value("TYPE S FOR DETAIL, U TO UPDATE ANY RECORD"));
    }

    @Test
    void ccli_aMalformedAccountFilterIsRejectedWithTheLegacyText() throws Exception {
        mockMvc.perform(get("/api/cards").param("acctId", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER"))
                .andExpect(jsonPath("$.fields[0]").value("acctId"));
    }

    @Test
    void ccli_selectionReturnsTheXctlTargetAnd204WhenNothingIsSelected() throws Exception {
        String body = """
                {"flags":["","S","","","","",""],
                 "rows":[{"acctId":"00000000050","cardNum":"0500024453765740","activeStatus":"Y"},
                         {"acctId":"00000000027","cardNum":"0683586198171516","activeStatus":"Y"}]}""";

        mockMvc.perform(post("/api/cards/selection").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.program").value("COCRDSLC"))
                .andExpect(jsonPath("$.tranId").value("CCDL"))
                .andExpect(jsonPath("$.cardNum").value("0683586198171516"));

        mockMvc.perform(post("/api/cards/selection").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"flags":["","","","","","",""],"rows":[]}"""))
                .andExpect(status().isNoContent());
    }

    @Test
    void ccdl_readsTheCardAndReportsAnUnknownOneAs404() throws Exception {
        mockMvc.perform(get("/api/cards/detail").param("acctId", ACCOUNT).param("cardNum", CARD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embossedName").value("Kelsie Dicki"))
                .andExpect(jsonPath("$.expiryMonth").value("05"))
                .andExpect(jsonPath("$.expiryYear").value("2024"))
                .andExpect(jsonPath("$.infoMessage").value("   Displaying requested details"));

        mockMvc.perform(get("/api/cards/detail").param("acctId", ACCOUNT).param("cardNum", "9999999999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Did not find cards for this search condition"));
    }

    @Test
    void ccup_loadThenValidateThenSave() throws Exception {
        mockMvc.perform(get("/api/cards/update").param("acctId", ACCOUNT).param("cardNum", CARD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SHOW_DETAILS"))
                .andExpect(jsonPath("$.embossedName").value("KELSIE DICKI"))
                .andExpect(jsonPath("$.message").value("Details of selected card shown above"));

        mockMvc.perform(post("/api/cards/update").contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CHANGES_OK_NOT_CONFIRMED"))
                .andExpect(jsonPath("$.message").value("Changes validated.Press F5 to save"));

        mockMvc.perform(post("/api/cards/update").contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CHANGES_OKAYED_AND_DONE"))
                .andExpect(jsonPath("$.message").value("Changes committed to database"))
                .andExpect(jsonPath("$.embossedName").value("KELSIE DICKIE"))
                // The expiry day is kept from the fetched record.
                .andExpect(jsonPath("$.expiryDay").value("20"));
    }

    @Test
    void ccup_anInvalidStatusIsRejectedWithTheLegacyText() throws Exception {
        String body = submitBody(true).replace("\"newStatus\":\"N\"", "\"newStatus\":\"y\"");

        mockMvc.perform(post("/api/cards/update").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Card Active Status must be Y or N"));
    }

    @Test
    void ccup_aRecordChangedMeanwhileIsRejectedWithTheRefreshedValues() throws Exception {
        CardRecord meanwhile = repository.findById(CARD).orElseThrow();
        meanwhile.setActiveStatus("N");
        repository.saveAndFlush(meanwhile);

        mockMvc.perform(post("/api/cards/update").contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Record changed by some one else. Please review"))
                .andExpect(jsonPath("$.refreshed.activeStatus").value("N"));
    }

    private static String submitBody(boolean confirmed) {
        return """
                {"acctId":"%s","cardNum":"%s",
                 "newName":"KELSIE DICKIE","newStatus":"N","newExpiryMonth":"06","newExpiryYear":"2026",
                 "oldName":"KELSIE DICKI","oldStatus":"Y","oldExpiryMonth":"05","oldExpiryYear":"2024",
                 "oldExpiryDay":"20","oldCvvCd":230,"confirmed":%s}"""
                .formatted(ACCOUNT, CARD, confirmed);
    }
}
