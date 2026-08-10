package com.carddemo.transaction;

import com.carddemo.transaction.entity.TransactionRecord;
import com.carddemo.transaction.repository.TransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 end-to-end integration for the Transaction Management stream. Walks the
 * cross-screen golden paths through the real controller -> service -> repository
 * -> H2 stack and asserts BOTH the API responses and the persisted H2 rows:
 *
 *   Flow 1  CT00 list -> CT01 view : first page returns 10 rows, then the row's
 *           id round-trips through GET /api/transactions/{id} (FR-L1, FR-L5, FR-V1).
 *   Flow 2  CT02 add  -> persisted -> CT01 view : POST writes a new row with the
 *           max-key+1 id, the row is present in H2, and GET /api/transactions/{id}
 *           serves it back (FR-A6, FR-A1, FR-V1).
 *
 * Uses the shared cached default {@code @SpringBootTest} context (MockMvc built
 * from the {@link WebApplicationContext}) so the JVM-persistent in-memory H2
 * (DB_CLOSE_DELAY=-1) is not re-seeded by a second context; {@code @Transactional}
 * rolls back the add so the seeded 30-row baseline is preserved for other tests.
 */
@SpringBootTest
@Transactional
class TransactionManagementE2EIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private TransactionRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void flow1_listFirstPageThenViewSelectedRow() throws Exception {
        MockMvc mockMvc = mockMvc();

        String listJson = mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(10))
                .andExpect(jsonPath("$.rows.length()").value(10))
                .andExpect(jsonPath("$.hasPrevPage").value(false))
                .andExpect(jsonPath("$.hasNextPage").value(true))
                .andReturn().getResponse().getContentAsString();

        JsonNode firstRow = objectMapper.readTree(listJson).get("rows").get(0);
        String selectedId = firstRow.get("id").asText();

        // FR-L5 select 'S' -> CT01 view of that row; API + persisted row agree.
        mockMvc.perform(get("/api/transactions/{id}", selectedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(selectedId))
                .andExpect(jsonPath("$.description").value(firstRow.get("description").asText()));

        TransactionRecord persisted = repository.findById(selectedId).orElseThrow();
        assertThat(persisted.getDescription()).isEqualTo(firstRow.get("description").asText());
    }

    @Test
    void flow2_addTransactionThenReadItBack() throws Exception {
        MockMvc mockMvc = mockMvc();

        long before = repository.count();

        String body = "{"
                + "\"accountId\":\"10000000001\","
                + "\"cardNumber\":\"\","
                + "\"typeCd\":\"07\","
                + "\"categoryCd\":\"1234\","
                + "\"source\":\"POS\","
                + "\"description\":\"E2E INTEGRATION TXN\","
                + "\"amount\":\"-00000123.45\","
                + "\"origDate\":\"2023-07-01\","
                + "\"procDate\":\"2023-07-02\","
                + "\"merchantId\":\"900000009\","
                + "\"merchantName\":\"E2E MERCHANT\","
                + "\"merchantCity\":\"DENVER\","
                + "\"merchantZip\":\"80202\","
                + "\"confirm\":\"Y\"}";

        String addJson = mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tranId").value("0000000000000031"))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(addJson).get("message").asText())
                .contains("Transaction added successfully")
                .contains("0000000000000031");

        // Persisted-row assertion: a real row was written (FR-A6) and card resolved (FR-A1).
        Optional<TransactionRecord> saved = repository.findById("0000000000000031");
        assertThat(saved).isPresent();
        assertThat(saved.get().getCardNum()).isEqualTo("4111111111111111");
        assertThat(repository.count()).isEqualTo(before + 1);

        // Read it back through CT01 view (FR-V1) — the add is visible cross-screen.
        mockMvc.perform(get("/api/transactions/{id}", "0000000000000031"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("E2E INTEGRATION TXN"))
                .andExpect(jsonPath("$.amountDisplay").value("-00000123.45"));
    }
}
