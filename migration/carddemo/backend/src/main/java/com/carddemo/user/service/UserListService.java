package com.carddemo.user.service;

import com.carddemo.common.domain.SecUserRecord;
import com.carddemo.user.UserMessages;
import com.carddemo.user.domain.UserFields;
import com.carddemo.user.dto.UserListResponse;
import com.carddemo.user.dto.UserListRow;
import com.carddemo.user.exception.UserStoreException;
import com.carddemo.user.repository.UserBrowseRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CU00 — List Users (COUSR00C).
 *
 * <p>Reproduces the USRSEC browse the legacy screen drives through
 * STARTBR/READNEXT/READPREV/ENDBR, but stateless: the COMMAREA paging cursor
 * CDEMO-CU00-INFO (cbl:67-76) becomes the {@code startId}/{@code dir} request
 * pair and the {@code firstId}/{@code lastId}/{@code hasNextPage}/
 * {@code hasPrevPage} response.
 *
 * <ul>
 *   <li>no dir — PROCESS-ENTER-KEY + PROCESS-PAGE-FORWARD (cbl:217-331): a blank
 *       Search User ID browses from the top, a value browses from the first id
 *       <em>at or after</em> it, because {@code STARTBR} defaults to GTEQ
 *       (the GTEQ operand is commented out at cbl:592) — FR-UL-1, FR-UL-2;</li>
 *   <li>{@code dir=next} — PF8: STARTBR at CDEMO-CU00-USRID-LAST plus the
 *       throwaway READNEXT that skips it (cbl:286-288), i.e. ids strictly
 *       greater than the last one shown — FR-UL-3;</li>
 *   <li>{@code dir=prev} — PF7: PROCESS-PAGE-BACKWARD reads 10 records back from
 *       CDEMO-CU00-USRID-FIRST and paints them into rows 10..1, i.e. the 10 ids
 *       below the first one shown, ascending — FR-UL-4.</li>
 * </ul>
 *
 * <p>The boundary literals ("You are already at the top of the page..." /
 * "...bottom...") are surfaced by the React screen from
 * {@code hasPrevPage}/{@code hasNextPage}, exactly as COUSR00C guards PF7 on
 * PAGE-NUM and PF8 on CDEMO-CU00-NEXT-PAGE-FLG (cbl:249-254, 271-276).
 */
@Service
public class UserListService {

    /** COUSR00C paints exactly 10 rows per screen (WS-USER-DATA OCCURS 10, cbl:56-63). */
    static final int PAGE_SIZE = 10;

    private final UserBrowseRepository repository;

    public UserListService(UserBrowseRepository repository) {
        this.repository = repository;
    }

    public UserListResponse list(String startId, String dir) {
        Pageable page = PageRequest.of(0, PAGE_SIZE);
        String direction = dir == null ? "" : dir.trim().toLowerCase();
        String cursor = UserFields.fit(startId, UserFields.USER_ID_LEN);

        try {
            List<SecUserRecord> records;
            if ("next".equals(direction)) {
                records = repository.findBySecUsrIdGreaterThanOrderBySecUsrIdAsc(cursor, page);
            } else if ("prev".equals(direction)) {
                List<SecUserRecord> back =
                        repository.findBySecUsrIdLessThanOrderBySecUsrIdDesc(cursor, page);
                Collections.reverse(back);
                records = back;
            } else if (cursor.isEmpty()) {
                records = repository.findByOrderBySecUsrIdAsc(page);
            } else {
                records = repository.findBySecUsrIdGreaterThanEqualOrderBySecUsrIdAsc(cursor, page);
            }

            List<UserListRow> rows = new ArrayList<>(records.size());
            for (SecUserRecord record : records) {
                rows.add(UserListRow.from(record));
            }

            String firstId = rows.isEmpty() ? null : rows.get(0).getUserId();
            String lastId = rows.isEmpty() ? null : rows.get(rows.size() - 1).getUserId();

            Pageable peek = PageRequest.of(0, 1);
            boolean hasNextPage = lastId != null
                    && !repository.findBySecUsrIdGreaterThanOrderBySecUsrIdAsc(lastId, peek).isEmpty();
            boolean hasPrevPage = firstId != null
                    && !repository.findBySecUsrIdLessThanOrderBySecUsrIdDesc(firstId, peek).isEmpty();

            return new UserListResponse(rows, firstId, lastId, hasNextPage, hasPrevPage);
        } catch (DataAccessException ex) {
            // STARTBR/READNEXT/READPREV WHEN OTHER (cbl:608-613, 642-647, 676-681).
            throw new UserStoreException(UserMessages.UNABLE_TO_LOOKUP_USER, ex);
        }
    }
}
