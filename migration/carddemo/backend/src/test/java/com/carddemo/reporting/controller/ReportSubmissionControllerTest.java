package com.carddemo.reporting.controller;

import com.carddemo.reporting.dto.ReportSubmitRequest;
import com.carddemo.reporting.port.TransactionReportJobLauncher;
import com.carddemo.reporting.port.TransactionReportJobRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level coverage of CR00's single action (FR-R6..FR-R39): the status codes,
 * the verbatim ERRMSG / success text, the cursor field, and what crosses the
 * S-07 → S-14 seam. The launcher is replaced by a recording fake so the seam can
 * be asserted; everything else is the real application context on H2.
 */
@SpringBootTest
class ReportSubmissionControllerTest {

    private static final String URL = "/api/reports/transactions";

    /** Stands in for the no-op adapter so the test can see what was launched. */
    @TestConfiguration
    static class RecordingLauncherConfiguration {

        private final List<TransactionReportJobRequest> submitted = new ArrayList<>();
        private boolean failing;

        @Bean
        @Primary
        TransactionReportJobLauncher recordingLauncher() {
            return request -> {
                if (failing) {
                    throw new IllegalStateException("job launcher unavailable");
                }
                submitted.add(request);
            };
        }
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingLauncherConfiguration launcherConfiguration;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        submittedJobs().clear();
        launcherConfiguration.failing = false;
    }

    private List<TransactionReportJobRequest> submittedJobs() {
        return launcherConfiguration.submitted;
    }

    private static ReportSubmitRequest monthly(String confirm) {
        ReportSubmitRequest request = new ReportSubmitRequest();
        request.setMonthly("S");
        request.setConfirm(confirm);
        return request;
    }

    private static ReportSubmitRequest custom(String confirm) {
        ReportSubmitRequest request = new ReportSubmitRequest();
        request.setCustom("S");
        request.setStartMonth("07");
        request.setStartDay("01");
        request.setStartYear("2023");
        request.setEndMonth("07");
        request.setEndDay("31");
        request.setEndYear("2023");
        request.setConfirm(confirm);
        return request;
    }

    private org.springframework.test.web.servlet.ResultActions submit(ReportSubmitRequest request)
            throws Exception {
        return mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    @Test
    @DisplayName("FR-R7: no report type marked -> 400 with the selection message")
    void noReportTypeSelected() throws Exception {
        submit(new ReportSubmitRequest())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Select a report type to print report..."))
                .andExpect(jsonPath("$.cursor").value("MONTHLY"));
    }

    @Test
    @DisplayName("FR-R29: a blank confirmation -> 400 naming the report")
    void blankConfirmation() throws Exception {
        submit(monthly(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Please confirm to print the Monthly report..."))
                .andExpect(jsonPath("$.cursor").value("CONFIRM"));

        assertThat(submittedJobs()).isEmpty();
    }

    @Test
    @DisplayName("FR-R32: an unrecognised confirmation is quoted back -> 400")
    void invalidConfirmation() throws Exception {
        submit(monthly("Q"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("\"Q\" is not a valid value to confirm..."));
    }

    @Test
    @DisplayName("FR-R12: an empty custom component -> 400 with the cursor on that field")
    void emptyCustomComponent() throws Exception {
        ReportSubmitRequest request = custom("Y");
        request.setStartMonth("");

        submit(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Start Date - Month can NOT be empty..."))
                .andExpect(jsonPath("$.cursor").value("SDTMM"));
    }

    @Test
    @DisplayName("FR-R18: a rejected screen redisplays the NUMVAL-C normalised components")
    void redisplaysNormalisedFields() throws Exception {
        ReportSubmitRequest request = custom("Y");
        request.setStartMonth("7");
        request.setStartDay("32");

        submit(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Start Date - Not a valid Day..."))
                .andExpect(jsonPath("$.dateFields.startMonth").value("07"))
                .andExpect(jsonPath("$.dateFields.startDay").value("32"))
                .andExpect(jsonPath("$.dateFields.startYear").value("2023"));
    }

    @Test
    @DisplayName("FR-R23: an impossible date -> 400 from the CSUTLDTC check")
    void invalidDate() throws Exception {
        ReportSubmitRequest request = custom("Y");
        request.setStartMonth("02");
        request.setStartDay("31");

        submit(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Start Date - Not a valid date..."));
    }

    @Test
    @DisplayName("FR-R30 / FR-R36 / FR-R37: Y -> 200, the success line, and one job launched")
    void confirmedMonthlySubmission() throws Exception {
        submit(monthly("Y"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submitted").value(true))
                .andExpect(jsonPath("$.message")
                        .value("Monthly report submitted for printing ..."))
                .andExpect(jsonPath("$.reportName").value("Monthly"));

        assertThat(submittedJobs()).singleElement().satisfies(job -> {
            assertThat(job.getReportName()).isEqualTo("Monthly");
            assertThat(job.getStartDate()).isEqualTo(job.getStartDate().withDayOfMonth(1));
        });
    }

    @Test
    @DisplayName("FR-R10 / FR-R37: a confirmed custom range crosses the seam as typed")
    void confirmedCustomSubmission() throws Exception {
        submit(custom("y"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2023-07-01"))
                .andExpect(jsonPath("$.endDate").value("2023-07-31"))
                .andExpect(jsonPath("$.message")
                        .value("Custom report submitted for printing ..."));

        assertThat(submittedJobs()).singleElement().satisfies(job ->
                assertThat(job.getEndDateText()).isEqualTo("2023-07-31"));
    }

    @Test
    @DisplayName("FR-R31: N -> 200, an empty screen, nothing launched")
    void declinedSubmission() throws Exception {
        submit(monthly("N"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submitted").value(false))
                .andExpect(jsonPath("$.message").value(""))
                .andExpect(jsonPath("$.fieldsCleared").value(true));

        assertThat(submittedJobs()).isEmpty();
    }

    @Test
    @DisplayName("FR-R35: a launch that fails shows the legacy TDQ error -> 502")
    void failedLaunch() throws Exception {
        launcherConfiguration.failing = true;

        submit(monthly("Y"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Unable to Write TDQ (JOBS)..."))
                .andExpect(jsonPath("$.cursor").value("MONTHLY"));
    }

    @Test
    @DisplayName("FR-R39: submitting a report reads and writes no business data")
    void touchesNoBusinessData() throws Exception {
        Integer before = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions", Integer.class);

        submit(monthly("Y")).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions", Integer.class)).isEqualTo(before);
    }
}
