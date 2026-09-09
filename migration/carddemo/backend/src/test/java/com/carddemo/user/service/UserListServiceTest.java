package com.carddemo.user.service;

import com.carddemo.user.dto.UserListResponse;
import com.carddemo.user.dto.UserListRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-UL-1..FR-UL-8 and the USRSEC fixture parity check, against the H2 database
 * seeded from {@code R__seed_carddemo_data.sql}, which carries the ten records
 * of the in-stream SYSUT1 data in app/jcl/DUSRSECJ.jcl:34-43 (the estate has no
 * ASCII USRSEC extract).
 */
@SpringBootTest
class UserListServiceTest {

    @Autowired
    private UserListService service;

    private static List<String> ids(UserListResponse response) {
        return response.getRows().stream().map(UserListRow::getUserId).toList();
    }

    @Test
    void frUL1_firstPage_browsesFromTheTopOfUsrsec() {
        UserListResponse r = service.list(null, null);

        assertThat(r.getCount()).isEqualTo(10);
        assertThat(ids(r)).containsExactly(
                "ADMIN001", "ADMIN002", "ADMIN003", "ADMIN004", "ADMIN005",
                "USER0001", "USER0002", "USER0003", "USER0004", "USER0005");
        assertThat(r.getFirstId()).isEqualTo("ADMIN001");
        assertThat(r.getLastId()).isEqualTo("USER0005");
        assertThat(r.isHasPrevPage()).isFalse();
        assertThat(r.isHasNextPage()).isFalse();
    }

    @Test
    void frUL17_rowsCarryTheDusrsecjFixtureValuesAndTheRawTypeCharacter() {
        UserListRow first = service.list(null, null).getRows().get(0);

        assertThat(first.getUserId()).isEqualTo("ADMIN001");
        assertThat(first.getFirstName()).isEqualTo("MARGARET");
        assertThat(first.getLastName()).isEqualTo("GOLD");
        assertThat(first.getUserType()).isEqualTo("A");
    }

    @Test
    void frUL2_searchKeyIsInclusive() {
        UserListResponse r = service.list("USER0002", null);

        assertThat(ids(r)).containsExactly("USER0002", "USER0003", "USER0004", "USER0005");
        assertThat(r.getFirstId()).isEqualTo("USER0002");
        assertThat(r.isHasPrevPage()).isTrue();
        assertThat(r.isHasNextPage()).isFalse();
    }

    @Test
    void frUL2_searchKeyWithNoExactMatch_startsAtTheNextId() {
        UserListResponse r = service.list("B", null);

        assertThat(ids(r)).containsExactly(
                "USER0001", "USER0002", "USER0003", "USER0004", "USER0005");
    }

    @Test
    void frUL7_searchKeyPastTheEnd_returnsAnEmptyPage() {
        UserListResponse r = service.list("ZZZZZZZZ", null);

        assertThat(r.getRows()).isEmpty();
        assertThat(r.getCount()).isZero();
        assertThat(r.getFirstId()).isNull();
        assertThat(r.getLastId()).isNull();
        assertThat(r.isHasNextPage()).isFalse();
        assertThat(r.isHasPrevPage()).isFalse();
    }

    @Test
    void frUL3_next_pagesForwardExclusiveOfTheLastIdShown() {
        UserListResponse r = service.list("ADMIN005", "next");

        assertThat(ids(r)).containsExactly(
                "USER0001", "USER0002", "USER0003", "USER0004", "USER0005");
        assertThat(r.isHasPrevPage()).isTrue();
        assertThat(r.isHasNextPage()).isFalse();
    }

    @Test
    void frUL4_prev_pagesBackwardExclusiveOfTheFirstIdShown_inAscendingOrder() {
        UserListResponse r = service.list("USER0001", "prev");

        assertThat(ids(r)).containsExactly(
                "ADMIN001", "ADMIN002", "ADMIN003", "ADMIN004", "ADMIN005");
        assertThat(r.getFirstId()).isEqualTo("ADMIN001");
        assertThat(r.getLastId()).isEqualTo("ADMIN005");
        assertThat(r.isHasPrevPage()).isFalse();
        assertThat(r.isHasNextPage()).isTrue();
    }

    @Test
    void frUL5_atTheTop_thePreviousPageFlagIsOff() {
        assertThat(service.list("ADMIN001", null).isHasPrevPage()).isFalse();
    }

    @Test
    void frUL6_atTheBottom_theNextPageFlagIsOff() {
        assertThat(service.list("USER0005", null).isHasNextPage()).isFalse();
    }

    @Test
    void frUL1_pageSizeIsTenRows() {
        assertThat(UserListService.PAGE_SIZE).isEqualTo(10);
    }

    @Test
    void frUL2_searchKeyLongerThanEightCharsIsTruncated() {
        assertThat(ids(service.list("USER0002XYZ", null))).startsWith("USER0002");
    }
}
