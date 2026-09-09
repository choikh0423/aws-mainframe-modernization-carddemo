package com.carddemo.pendingauth.service;

import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.CustomerRecord;
import com.carddemo.common.domain.PendingAuthDetailRecord;
import com.carddemo.common.domain.PendingAuthSummaryRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.CardXrefRepository;
import com.carddemo.common.repository.CustomerRepository;
import com.carddemo.common.repository.PendingAuthSummaryRepository;
import com.carddemo.pendingauth.dto.PendingAuthListResponse;
import com.carddemo.pendingauth.dto.PendingAuthListRow;
import com.carddemo.pendingauth.dto.PendingAuthSelectionRequest;
import com.carddemo.pendingauth.dto.PendingAuthSelectionResponse;
import com.carddemo.pendingauth.repository.PendingAuthDetailBrowseRepository;
import com.carddemo.pendingauth.util.PendingAuthFormat;
import com.carddemo.pendingauth.util.PendingAuthKey;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CPVS / COPAUS0C — the pending authorization summary and list screen.
 *
 * <p>The legacy program pages a DL/I parentage: {@code GU PAUTSUM0} for the
 * account, then {@code GNP PAUTDTL1} five times per screen with one extra read
 * to light the "next page" flag (PROCESS-PAGE-FORWARD, COPAUS0C.cbl:414-451).
 * PF7 repositions on the first key of the previous page, PF8 on the last key of
 * the current one. Here the same cursor is expressed as key-range queries so no
 * server-side position is kept between requests (BD-1).
 */
@Service
public class PendingAuthListService {

    /** The five list lines of COPAU0A. */
    public static final int PAGE_SIZE = 5;

    private final CardXrefRepository cardXrefRepository;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final PendingAuthSummaryRepository summaryRepository;
    private final PendingAuthDetailBrowseRepository detailRepository;

    public PendingAuthListService(CardXrefRepository cardXrefRepository,
                                  AccountRepository accountRepository,
                                  CustomerRepository customerRepository,
                                  PendingAuthSummaryRepository summaryRepository,
                                  PendingAuthDetailBrowseRepository detailRepository) {
        this.cardXrefRepository = cardXrefRepository;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.summaryRepository = summaryRepository;
        this.detailRepository = detailRepository;
    }

    /** How the caller reached this page, i.e. which AID the legacy screen saw. */
    public enum Direction {
        /** ENTER / first display: the top of the chain. */
        FIRST,
        /** PF8: the segments after {@code startKey}. */
        NEXT,
        /** PF7: the page that starts at {@code startKey}. */
        PREVIOUS
    }

    /**
     * PROCESS-ENTER-KEY's account validation followed by GATHER-DETAILS
     * (COPAUS0C.cbl:262-357).
     */
    @Transactional(readOnly = true)
    public PendingAuthListResponse list(String acctIdInput, Direction direction, String startKey,
                                        int pageNum) {
        if (acctIdInput == null || acctIdInput.trim().isEmpty()) {
            return PendingAuthListResponse.message(PendingAuthMessages.ENTER_ACCT_ID);
        }
        String trimmed = acctIdInput.trim();
        if (!trimmed.chars().allMatch(Character::isDigit)) {
            return PendingAuthListResponse.message(PendingAuthMessages.ACCT_ID_NOT_NUMERIC);
        }
        long acctId = Long.parseLong(trimmed);
        String acctId11 = PendingAuthFormat.acctId11(acctId);

        Optional<CardXrefRecord> xref = cardXrefRepository.findByAcctId(acctId);
        if (xref.isEmpty()) {
            return PendingAuthListResponse.message(PendingAuthMessages.acctNotFoundInXref(acctId11));
        }
        Optional<AccountRecord> account = accountRepository.findById(xref.get().getAcctId());
        if (account.isEmpty()) {
            return PendingAuthListResponse.message(PendingAuthMessages.acctNotFoundInAcct(acctId11));
        }
        Optional<CustomerRecord> customer = customerRepository.findById(xref.get().getCustId());
        if (customer.isEmpty()) {
            return PendingAuthListResponse.message(PendingAuthMessages
                    .custNotFoundInCust(PendingAuthFormat.custId9(xref.get().getCustId())));
        }

        PendingAuthSummaryRecord summary = summaryRepository.findById(acctId).orElse(null);
        Page page = readPage(acctId, direction, startKey, summary != null);
        return render(acctId11, account.get(), customer.get(), summary, page,
                nextPageNumber(direction, pageNum, page), page.message);
    }

    /**
     * The selection evaluation of PROCESS-ENTER-KEY: the first non-blank flag
     * wins (quirk Q-10), {@code S}/{@code s} transfers to CPVD and anything else
     * leaves the "Invalid selection" message on CPVS (COPAUS0C.cbl:288-341).
     */
    @Transactional(readOnly = true)
    public PendingAuthSelectionResponse select(PendingAuthSelectionRequest request) {
        String acctIdInput = request.acctId();
        if (acctIdInput == null || acctIdInput.trim().isEmpty()) {
            return PendingAuthSelectionResponse.stay(PendingAuthMessages.ENTER_ACCT_ID);
        }
        if (!acctIdInput.trim().chars().allMatch(Character::isDigit)) {
            return PendingAuthSelectionResponse.stay(PendingAuthMessages.ACCT_ID_NOT_NUMERIC);
        }
        List<PendingAuthSelectionRequest.Row> rows =
                request.rows() == null ? List.of() : request.rows();
        for (PendingAuthSelectionRequest.Row row : rows) {
            String flag = row.flag();
            if (flag == null || flag.trim().isEmpty()) {
                continue;
            }
            String key = row.authKey();
            if (key == null || key.trim().isEmpty()) {
                // A flag on a row with no key leaves CDEMO-CPVS-PAU-SELECTED blank
                // and the IF at COPAUS0C.cbl:319 is false, so the screen just redisplays.
                return PendingAuthSelectionResponse.stay(null);
            }
            if ("S".equalsIgnoreCase(flag.trim())) {
                return PendingAuthSelectionResponse.xctl(key.trim());
            }
            return PendingAuthSelectionResponse.stay(PendingAuthMessages.INVALID_SELECTION);
        }
        return PendingAuthSelectionResponse.stay(null);
    }

    private record Page(List<PendingAuthDetailRecord> rows, boolean nextPage, String message) {
    }

    private Page readPage(long acctId, Direction direction, String startKey, boolean summaryFound) {
        if (!summaryFound) {
            // NFOUND-PAUT-SMRY-SEG: GATHER-DETAILS skips PROCESS-PAGE-FORWARD entirely.
            return new Page(List.of(), false, null);
        }
        PageRequest window = PageRequest.of(0, PAGE_SIZE + 1);
        List<PendingAuthDetailRecord> found;
        switch (direction) {
            case NEXT -> {
                if (!PendingAuthKey.isValid(startKey)) {
                    found = detailRepository.browse(acctId, window);
                } else {
                    found = detailRepository.browseAfter(acctId, PendingAuthKey.date9c(startKey),
                            PendingAuthKey.time9c(startKey), window);
                    if (found.isEmpty()) {
                        return new Page(List.of(), false, PendingAuthMessages.BOTTOM_OF_PAGE);
                    }
                }
            }
            case PREVIOUS -> {
                if (!PendingAuthKey.isValid(startKey)) {
                    return new Page(List.of(), false, PendingAuthMessages.TOP_OF_PAGE);
                }
                found = detailRepository.browseFrom(acctId, PendingAuthKey.date9c(startKey),
                        PendingAuthKey.time9c(startKey), window);
            }
            default -> found = detailRepository.browse(acctId, window);
        }
        boolean nextPage = found.size() > PAGE_SIZE;
        List<PendingAuthDetailRecord> rows =
                new ArrayList<>(found.subList(0, Math.min(PAGE_SIZE, found.size())));
        return new Page(rows, nextPage, null);
    }

    private int nextPageNumber(Direction direction, int pageNum, Page page) {
        if (page.rows.isEmpty()) {
            return direction == Direction.FIRST ? 0 : Math.max(pageNum, 0);
        }
        return switch (direction) {
            case FIRST -> 1;
            case NEXT -> pageNum + 1;
            case PREVIOUS -> Math.max(pageNum - 1, 1);
        };
    }

    private PendingAuthListResponse render(String acctId11, AccountRecord account,
                                           CustomerRecord customer,
                                           PendingAuthSummaryRecord summary, Page page,
                                           int pageNum, String message) {
        List<PendingAuthListRow> rows = page.rows.stream().map(PendingAuthListRow::from).toList();
        String firstKey = rows.isEmpty() ? null : rows.get(0).authKey();
        String lastKey = rows.isEmpty() ? null : rows.get(rows.size() - 1).authKey();

        boolean hasSummary = summary != null;
        return new PendingAuthListResponse(
                acctId11,
                PendingAuthFormat.custId9(customer.getCustId()),
                customerName(customer),
                addressLine1(customer),
                addressLine2(customer),
                customer.getPhoneNum1(),
                PendingAuthFormat.amount12(account.getCreditLimit()),
                PendingAuthFormat.amount9(account.getCashCreditLimit()),
                hasSummary ? PendingAuthFormat.amount12(summary.getPaCreditBalance()) : zero12(),
                hasSummary ? PendingAuthFormat.amount9(summary.getPaCashBalance()) : zero9(),
                hasSummary ? PendingAuthFormat.count3(summary.getPaApprovedAuthCnt()) : "000",
                hasSummary ? PendingAuthFormat.count3(summary.getPaDeclinedAuthCnt()) : "000",
                hasSummary ? PendingAuthFormat.amount9(summary.getPaApprovedAuthAmt()) : zero9(),
                hasSummary ? PendingAuthFormat.amount9(summary.getPaDeclinedAuthAmt()) : zero9(),
                rows, pageNum, firstKey, lastKey, page.nextPage, message);
    }

    /** {@code MOVE ZERO TO CREDBALO} etc. when there is no summary segment. */
    private String zero12() {
        return PendingAuthFormat.amount12(BigDecimal.ZERO);
    }

    private String zero9() {
        return PendingAuthFormat.amount9(BigDecimal.ZERO);
    }

    /** {@code STRING CUST-FIRST-NAME ' ' CUST-MIDDLE-NAME(1:1) ' ' CUST-LAST-NAME}. */
    private String customerName(CustomerRecord c) {
        String middle = c.getMiddleName() == null || c.getMiddleName().isEmpty()
                ? " " : c.getMiddleName().substring(0, 1);
        return trim(c.getFirstName()) + " " + middle + " " + trim(c.getLastName());
    }

    /** {@code STRING CUST-ADDR-LINE-1 ',' CUST-ADDR-LINE-2}. */
    private String addressLine1(CustomerRecord c) {
        return trim(c.getAddrLine1()) + "," + trim(c.getAddrLine2());
    }

    /** {@code STRING CUST-ADDR-LINE-3 ',' CUST-ADDR-STATE-CD ',' CUST-ADDR-ZIP(1:5)}. */
    private String addressLine2(CustomerRecord c) {
        String zip = trim(c.getAddrZip());
        if (zip.length() > 5) {
            zip = zip.substring(0, 5);
        }
        return trim(c.getAddrLine3()) + "," + trim(c.getAddrStateCd()) + "," + zip;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
