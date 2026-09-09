package com.carddemo.transaction.service;

import com.carddemo.transaction.dto.TransactionListResponse;
import com.carddemo.transaction.dto.TransactionListRow;
import com.carddemo.common.domain.TransactionRecord;
import com.carddemo.transaction.exception.NonNumericTranIdException;
import com.carddemo.common.repository.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CT00 — List Transactions (COTRN00C).
 *
 * Reproduces the TRANSACT browse COTRN00C drives through STARTBR/READNEXT/
 * READPREV 1:1, but stateless: the CICS COMMAREA paging cursor
 * (CDEMO-CT00-INFO) becomes the {@code startId}/{@code dir} request pair and the
 * {@code firstId}/{@code lastId}/{@code hasNextPage}/{@code hasPrevPage} response.
 *
 *   - no dir  -> PROCESS-ENTER-KEY + PROCESS-PAGE-FORWARD (COTRN00C.cbl:206-328):
 *       blank filter  -> browse from the top (STARTBR LOW-VALUES);
 *       numeric filter-> STARTBR GTEQ, list begins AT/after that Tran ID (FR-L2);
 *       non-numeric   -> "Tran ID must be Numeric ..." (FR-L7, cbl:209-218).
 *   - dir=next -> PF8/PROCESS-PAGE-FORWARD from CDEMO-CT00-TRNID-LAST: STARTBR at
 *       the last shown id, throwaway READNEXT to skip it, then read 10 forward
 *       (FR-L3, cbl:257-268, 285-303) == rows with id > startId.
 *   - dir=prev -> PF7/PROCESS-PAGE-BACKWARD from CDEMO-CT00-TRNID-FIRST: STARTBR
 *       at the first shown id, throwaway READPREV to skip it, then read 10
 *       backward and reverse into ascending order (FR-L4, cbl:234-246, 333-374)
 *       == the 10 rows with id < startId.
 *
 * The boundary messages ("...bottom of the page..." / "...top of the page...")
 * are surfaced by the React screen from {@code hasNextPage}/{@code hasPrevPage}.
 */
@Service
public class TransactionListService {

    /** COTRN00C reads exactly 10 rows per screen (COTRN00C.cbl:290-303). */
    static final int PAGE_SIZE = 10;

    /** TRAN-ID is a 16-char zero-padded numeric key (CVTRA05Y / schema.sql). */
    private static final int TRAN_ID_LEN = 16;

    private final TransactionRepository repository;

    public TransactionListService(TransactionRepository repository) {
        this.repository = repository;
    }

    public TransactionListResponse list(String startId, String dir) {
        Pageable page = PageRequest.of(0, PAGE_SIZE);
        String direction = dir == null ? "" : dir.trim().toLowerCase();

        List<TransactionRecord> records;
        if ("next".equals(direction)) {
            // PF8: page forward from the last id shown (exclusive).
            records = repository.findByIdGreaterThanOrderByIdAsc(cursor(startId), page);
        } else if ("prev".equals(direction)) {
            // PF7: the 10 rows immediately before the first id shown, back in order.
            List<TransactionRecord> back = repository.findByIdLessThanOrderByIdDesc(cursor(startId), page);
            Collections.reverse(back);
            records = back;
        } else {
            // ENTER: initial open / apply filter.
            String filter = startId == null ? "" : startId.trim();
            if (filter.isEmpty()) {
                records = repository.findAllByOrderByIdAsc(page);
            } else {
                if (!filter.chars().allMatch(Character::isDigit)) {
                    throw new NonNumericTranIdException();
                }
                // STARTBR GTEQ: list begins AT/after the Tran ID (inclusive).
                records = repository.findByIdGreaterThanEqualOrderByIdAsc(normalizeKey(filter), page);
            }
        }

        List<TransactionListRow> rows = new ArrayList<>(records.size());
        for (TransactionRecord r : records) {
            rows.add(TransactionListRow.from(r));
        }

        String firstId = records.isEmpty() ? null : records.get(0).getId();
        String lastId = records.isEmpty() ? null : records.get(records.size() - 1).getId();

        boolean hasNextPage = lastId != null
                && !repository.findByIdGreaterThanOrderByIdAsc(lastId, PageRequest.of(0, 1)).isEmpty();
        boolean hasPrevPage = firstId != null
                && !repository.findByIdLessThanOrderByIdDesc(firstId, PageRequest.of(0, 1)).isEmpty();

        return new TransactionListResponse(rows, firstId, lastId, hasNextPage, hasPrevPage);
    }

    /**
     * The PF7/PF8 cursor is always an actual 16-char row id echoed back by a
     * prior page, but normalise defensively so a short/padded value still keys
     * correctly against the zero-padded ids.
     */
    private String cursor(String id) {
        return normalizeKey(id == null ? "" : id.trim());
    }

    /** Left-pad a numeric Tran ID to the 16-char VSAM key width. */
    private String normalizeKey(String id) {
        if (id.length() >= TRAN_ID_LEN) {
            return id;
        }
        StringBuilder sb = new StringBuilder(TRAN_ID_LEN);
        for (int i = id.length(); i < TRAN_ID_LEN; i++) {
            sb.append('0');
        }
        return sb.append(id).toString();
    }
}
