package com.carddemo.statements.service;

import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.CustomerRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.CardXrefRepository;
import com.carddemo.common.repository.CustomerRepository;
import com.carddemo.statements.StatementArchiveRecord;
import com.carddemo.statements.StatementArchiveRepository;
import com.carddemo.statements.StatementFormat;
import com.carddemo.statements.StatementMessages;
import com.carddemo.statements.dto.StatementDetailResponse;
import com.carddemo.statements.dto.StatementPageResponse;
import com.carddemo.statements.dto.StatementPdf;
import com.carddemo.statements.dto.StatementSummary;
import com.carddemo.statements.exception.StatementNotFoundException;
import com.carddemo.statements.exception.StatementRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the statement archive for the list, detail, HTML and PDF endpoints. */
@Service
@Transactional(readOnly = true)
public class StatementService {

    public static final int PAGE_SIZE = 10;

    private final StatementArchiveRepository archive;
    private final AccountRepository accounts;
    private final CardXrefRepository cardXrefs;
    private final CustomerRepository customers;
    private final StatementPdfRenderer pdfRenderer;

    public StatementService(StatementArchiveRepository archive, AccountRepository accounts,
                            CardXrefRepository cardXrefs, CustomerRepository customers,
                            StatementPdfRenderer pdfRenderer) {
        this.archive = archive;
        this.accounts = accounts;
        this.cardXrefs = cardXrefs;
        this.customers = customers;
        this.pdfRenderer = pdfRenderer;
    }

    public StatementPageResponse list(String acctId, String page) {
        long accountId = editAccountId(acctId);
        int pageNumber = editPage(page);
        String accountId11 = StatementFormat.accountId(accountId);
        if (!accounts.existsById(accountId)) {
            throw new StatementNotFoundException(StatementMessages.accountNotFound(accountId11));
        }
        Page<StatementArchiveRecord> result =
                archive.findByAcctIdOrderByCycleDesc(accountId, PageRequest.of(pageNumber, PAGE_SIZE));
        return new StatementPageResponse(accountId11, pageNumber, PAGE_SIZE,
                result.getTotalElements(), result.getTotalPages(), result.hasNext(),
                result.hasPrevious(), result.map(StatementSummary::from).getContent());
    }

    public StatementDetailResponse detail(String stmtId) {
        StatementArchiveRecord statement = find(stmtId);
        AccountRecord account = accounts.findById(statement.getAcctId()).orElse(null);
        CustomerRecord customer = cardXrefs.findById(statement.getCardNum())
                .or(() -> cardXrefs.findByAcctId(statement.getAcctId()))
                .map(CardXrefRecord::getCustId)
                .flatMap(customers::findById)
                .orElse(null);
        return StatementDetailResponse.from(statement, account, customer);
    }

    public String html(String stmtId) {
        return find(stmtId).getHtmlBody();
    }

    public StatementPdf pdf(String stmtId) {
        StatementArchiveRecord statement = find(stmtId);
        String name = "STMT-" + StatementFormat.accountId(statement.getAcctId()) + "-"
                + statement.getCycle();
        return new StatementPdf(name + ".pdf", pdfRenderer.render(statement.getTextBody(), name));
    }

    private StatementArchiveRecord find(String stmtId) {
        long id = editStatementId(stmtId);
        return archive.findById(id)
                .orElseThrow(() -> new StatementNotFoundException(StatementMessages.statementNotFound(id)));
    }

    /** The COACTVWC 2210-EDIT-ACCOUNT rule: 11 numeric digits, not all zero. */
    static long editAccountId(String acctId) {
        String edited = acctId == null ? "" : acctId.trim();
        if (edited.isEmpty()) {
            throw new StatementRequestException(StatementMessages.NO_INPUT_RECEIVED);
        }
        if (!edited.matches("[0-9]{11}") || Long.parseLong(edited) == 0L) {
            throw new StatementRequestException(StatementMessages.INVALID_ACCOUNT_FILTER);
        }
        return Long.parseLong(edited);
    }

    static int editPage(String page) {
        if (page == null || page.isBlank()) {
            return 0;
        }
        String edited = page.trim();
        if (!edited.matches("[0-9]{1,6}")) {
            throw new StatementRequestException(StatementMessages.INVALID_PAGE);
        }
        return Integer.parseInt(edited);
    }

    static long editStatementId(String stmtId) {
        String edited = stmtId == null ? "" : stmtId.trim();
        if (!edited.matches("[0-9]{1,18}") || Long.parseLong(edited) == 0L) {
            throw new StatementRequestException(StatementMessages.INVALID_STATEMENT_ID);
        }
        return Long.parseLong(edited);
    }
}
