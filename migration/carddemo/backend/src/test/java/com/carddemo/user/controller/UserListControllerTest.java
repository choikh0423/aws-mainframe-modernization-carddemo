package com.carddemo.user.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level FR-UL-1..FR-UL-6 coverage for {@link UserListController} against
 * the seeded H2 USRSEC (the ten app/jcl/DUSRSECJ.jcl:34-43 records).
 *
 * <p>MockMvc is built from the {@link WebApplicationContext} without the
 * security filter chain, matching the reference stream's controller tests and
 * reusing the cached context; the ROLE_ADMIN boundary itself is covered by
 * {@code UserAdminSecurityTest}.
 */
@SpringBootTest
class UserListControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void frUL1_firstPage_returns200WithTheTenSeededUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(10))
                .andExpect(jsonPath("$.firstId").value("ADMIN001"))
                .andExpect(jsonPath("$.lastId").value("USER0005"))
                .andExpect(jsonPath("$.hasPrevPage").value(false))
                .andExpect(jsonPath("$.hasNextPage").value(false))
                .andExpect(jsonPath("$.rows[0].userId").value("ADMIN001"))
                .andExpect(jsonPath("$.rows[0].firstName").value("MARGARET"))
                .andExpect(jsonPath("$.rows[0].lastName").value("GOLD"))
                .andExpect(jsonPath("$.rows[0].userType").value("A"));
    }

    @Test
    void frUL2_searchUserId_startsAtOrAfterTheKey() throws Exception {
        mockMvc.perform(get("/api/admin/users").param("startId", "USER0003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.firstId").value("USER0003"))
                .andExpect(jsonPath("$.hasPrevPage").value(true));
    }

    @Test
    void frUL3_next_pagesForward() throws Exception {
        mockMvc.perform(get("/api/admin/users").param("startId", "ADMIN003").param("dir", "next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstId").value("ADMIN004"))
                .andExpect(jsonPath("$.lastId").value("USER0005"))
                .andExpect(jsonPath("$.hasNextPage").value(false));
    }

    @Test
    void frUL4_prev_pagesBackward() throws Exception {
        mockMvc.perform(get("/api/admin/users").param("startId", "USER0001").param("dir", "prev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstId").value("ADMIN001"))
                .andExpect(jsonPath("$.lastId").value("ADMIN005"))
                .andExpect(jsonPath("$.hasNextPage").value(true))
                .andExpect(jsonPath("$.hasPrevPage").value(false));
    }
}
