package com.carddemo.card.service;

import com.carddemo.card.dto.CardListResponse;
import com.carddemo.card.dto.CardListRow;
import com.carddemo.card.message.CardManagementMessages;
import com.carddemo.card.repository.CardBrowseRepository;
import com.carddemo.card.validator.CardSearchKeyValidator;
import com.carddemo.common.domain.CardRecord;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CCLI — List Credit Cards (COCRDLIC).
 *
 * <p>Reproduces the CARDDAT browse 9000-READ-FORWARD / 9100-READ-BACKWARDS
 * drive, but stateless: the private COMMAREA page state (WS-CA-SCREEN-NUM,
 * WS-CA-FIRST-CARD-NUM, WS-CA-LAST-CARD-NUM, CA-NEXT-PAGE-*, CA-LAST-PAGE-*)
 * travels on the request and comes back on the response.
 *
 * <ul>
 *   <li>no direction — ENTER: browse from the start of the file, page 1;</li>
 *   <li>{@code F} with a next page — PF8: browse forward from
 *       {@code lastCardNum}, which the previous page's look-ahead READNEXT left
 *       pointing at the first record of this page (COCRDLIC.cbl:486-497,
 *       1197-1214), page + 1;</li>
 *   <li>{@code F} with no next page — the EVALUATE falls through to WHEN OTHER
 *       and re-reads the same page from {@code firstCardNum}
 *       (COCRDLIC.cbl:572-582);</li>
 *   <li>{@code B} on page 2+ — PF7: READPREV the seven records before
 *       {@code firstCardNum} (COCRDLIC.cbl:501-513, 1294-1371), page - 1;</li>
 *   <li>{@code B} on page 1 — re-reads page 1 forward from {@code firstCardNum}
 *       (COCRDLIC.cbl:444-454).</li>
 * </ul>
 *
 * <p>Filtering matches 9500-FILTER-RECORDS: the browse is by card-number key
 * and the account/card filters are applied to each record read, not used as a
 * key (COCRDLIC.cbl:1382-1405).
 */
@Service
public class CardListService {

    /** WS-MAX-SCREEN-LINES (COCRDLIC.cbl:177-178). */
    static final int PAGE_SIZE = 7;

    /** How many records to pull per repository round trip while filtering. */
    private static final int READ_CHUNK = 50;

    private static final String LOW_VALUES = "";

    private final CardBrowseRepository repository;
    private final CardSearchKeyValidator keyValidator;

    public CardListService(CardBrowseRepository repository, CardSearchKeyValidator keyValidator) {
        this.repository = repository;
        this.keyValidator = keyValidator;
    }

    public CardListResponse list(String acctFilter, String cardFilter, String dir, Integer pageNumber,
                                 String firstCardNum, String lastCardNum,
                                 Boolean nextPageExists, Boolean lastPageShown) {

        // 2210/2220-EDIT-ACCOUNT/CARD: a bad filter suppresses the browse entirely.
        String account = keyValidator.optionalAccountFilter(acctFilter);
        String card = keyValidator.optionalCardFilter(cardFilter);

        String direction = dir == null ? "" : dir.trim().toUpperCase();
        int page = pageNumber == null || pageNumber < 1 ? 1 : pageNumber;
        boolean priorNextPage = Boolean.TRUE.equals(nextPageExists);
        // Only PF8 keeps the "last page already shown" flag (COCRDLIC.cbl:410-414).
        boolean priorLastPageShown = "F".equals(direction) && Boolean.TRUE.equals(lastPageShown);

        boolean backwardOnFirstPage = "B".equals(direction) && page <= 1;

        List<CardRecord> records;
        boolean hasNextPage;
        String nextPageKey;
        if ("B".equals(direction) && page > 1) {
            page = page - 1;
            records = readBackwards(firstCardNum, account, card);
            // 9100-READ-BACKWARDS sets CA-NEXT-PAGE-EXISTS and never clears it, and
            // leaves the last key pointing at the page we came from (line 1268).
            hasNextPage = true;
            nextPageKey = firstCardNum;
        } else {
            String startKey;
            if ("F".equals(direction) && priorNextPage) {
                startKey = lastCardNum;
                page = page + 1;
            } else if (direction.isEmpty()) {
                startKey = LOW_VALUES;
                page = 1;
            } else {
                startKey = firstCardNum == null ? LOW_VALUES : firstCardNum;
            }
            List<CardRecord> found = readForward(startKey, account, card, PAGE_SIZE + 1);
            hasNextPage = found.size() > PAGE_SIZE;
            // The look-ahead READNEXT is what WS-CA-LAST-CARD-NUM ends up holding.
            nextPageKey = hasNextPage ? found.get(PAGE_SIZE).getCardNum() : lastCardNum;
            records = hasNextPage ? found.subList(0, PAGE_SIZE) : found;
        }

        List<CardListRow> rows = new ArrayList<>(records.size());
        for (CardRecord record : records) {
            rows.add(CardListRow.from(record));
        }

        // The look-ahead / read loop hitting ENDFILE (COCRDLIC.cbl:1215-1221, 1233-1240).
        // An empty page 1 sets WS-NO-RECORDS-FOUND, but 1100-SCREEN-INIT clears
        // WS-INFO-MSG before 1400-SETUP-MESSAGE reads it, so that literal never
        // reaches the screen and the operator sees this message instead (quirk Q-11).
        String error = hasNextPage ? null : CardManagementMessages.NO_MORE_RECORDS;

        // 1400-SETUP-MESSAGE (COCRDLIC.cbl:895-930).
        // The flags 1400 tests are the ones the read just left behind, so the
        // "last page" latch is set on the PF8 that first reaches the end of the
        // browse and "NO MORE PAGES TO DISPLAY" only on the PF8 after that.
        String info = CardManagementMessages.INFORM_REC_ACTIONS;
        boolean forwardAtEnd = "F".equals(direction) && !hasNextPage;
        boolean lastShown = priorLastPageShown;
        if (backwardOnFirstPage) {
            error = CardManagementMessages.NO_PREVIOUS_PAGES;
        } else if (forwardAtEnd && priorLastPageShown) {
            error = CardManagementMessages.NO_MORE_PAGES;
        } else if (forwardAtEnd) {
            lastShown = true;
        }

        String first = records.isEmpty() ? firstCardNum : records.get(0).getCardNum();

        return new CardListResponse(rows, page, first, nextPageKey, hasNextPage, lastShown, info, error);
    }

    /** STARTBR GTEQ at {@code startKey} then READNEXT, applying 9500-FILTER-RECORDS. */
    private List<CardRecord> readForward(String startKey, String account, String card, int limit) {
        List<CardRecord> kept = new ArrayList<>(limit);
        String key = startKey == null ? LOW_VALUES : startKey;
        boolean inclusive = true;
        while (kept.size() < limit) {
            List<CardRecord> chunk = inclusive
                    ? repository.findByCardNumGreaterThanEqualOrderByCardNumAsc(key, PageRequest.of(0, READ_CHUNK))
                    : repository.findByCardNumGreaterThanOrderByCardNumAsc(key, PageRequest.of(0, READ_CHUNK));
            if (chunk.isEmpty()) {
                break;
            }
            for (CardRecord record : chunk) {
                if (include(record, account, card) && kept.size() < limit) {
                    kept.add(record);
                }
            }
            if (chunk.size() < READ_CHUNK) {
                break;
            }
            key = chunk.get(chunk.size() - 1).getCardNum();
            inclusive = false;
        }
        return kept;
    }

    /**
     * The first READPREV skips the record the browse is positioned on
     * (COCRDLIC.cbl:1294-1307), so the page is the seven kept records strictly
     * before {@code firstCardNum}, returned in ascending order.
     */
    private List<CardRecord> readBackwards(String firstCardNum, String account, String card) {
        List<CardRecord> kept = new ArrayList<>(PAGE_SIZE);
        String key = firstCardNum;
        while (kept.size() < PAGE_SIZE && key != null) {
            List<CardRecord> chunk =
                    repository.findByCardNumLessThanOrderByCardNumDesc(key, PageRequest.of(0, READ_CHUNK));
            if (chunk.isEmpty()) {
                break;
            }
            for (CardRecord record : chunk) {
                if (include(record, account, card) && kept.size() < PAGE_SIZE) {
                    kept.add(record);
                }
            }
            if (chunk.size() < READ_CHUNK) {
                break;
            }
            key = chunk.get(chunk.size() - 1).getCardNum();
        }
        Collections.reverse(kept);
        return kept;
    }


    /** 9500-FILTER-RECORDS: independent account and card equality tests. */
    private boolean include(CardRecord record, String account, String card) {
        if (account != null && Long.parseLong(account) != record.getAcctId()) {
            return false;
        }
        return card == null || card.equals(record.getCardNum());
    }
}
