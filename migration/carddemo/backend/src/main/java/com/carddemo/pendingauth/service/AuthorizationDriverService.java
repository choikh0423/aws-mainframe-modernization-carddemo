package com.carddemo.pendingauth.service;

import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.PendingAuthDetailId;
import com.carddemo.common.domain.PendingAuthDetailRecord;
import com.carddemo.common.domain.PendingAuthSummaryRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.CardXrefRepository;
import com.carddemo.common.repository.CustomerRepository;
import com.carddemo.common.repository.PendingAuthDetailRepository;
import com.carddemo.common.repository.PendingAuthSummaryRepository;
import com.carddemo.pendingauth.dto.AuthorizationRequest;
import com.carddemo.pendingauth.util.PendingAuthFormat;
import com.carddemo.pendingauth.util.PendingAuthKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * CP00 / COPAUA0C — the MQ-driven authorization driver.
 *
 * <p>One JMS message is one iteration of 2000-MAIN-PROCESS: extract the request
 * (2100), process the authorization (5000: XREF, ACCT, CUST and IMS summary
 * reads, then 6000-MAKE-DECISION), build the reply, and — only when the card
 * was found in the XREF — write the summary and the detail segment (8000).
 * The CICS SYNCPOINT after each message is the transaction boundary of
 * {@link #process(String)}.
 *
 * <p>The declined-amount accumulation deliberately reproduces quirk Q-6: 8400
 * adds {@code PA-TRANSACTION-AMT}, the detail work area field that 8500 only
 * populates afterwards, so a declined authorization accumulates the
 * <em>previous</em> message's transaction amount (zero for the first message of
 * the run). {@code detailWorkAreaTransactionAmt} is that piece of WORKING-STORAGE.
 */
@Service
public class AuthorizationDriverService {

    private final CardXrefRepository cardXrefRepository;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final PendingAuthSummaryRepository summaryRepository;
    private final PendingAuthDetailRepository detailRepository;
    private final AuthorizationErrorLog errorLog;
    private final Clock clock;

    /** {@code PA-TRANSACTION-AMT} of {@code PENDING-AUTH-DETAILS} between messages. */
    private BigDecimal detailWorkAreaTransactionAmt = BigDecimal.ZERO;

    public AuthorizationDriverService(CardXrefRepository cardXrefRepository,
                                      AccountRepository accountRepository,
                                      CustomerRepository customerRepository,
                                      PendingAuthSummaryRepository summaryRepository,
                                      PendingAuthDetailRepository detailRepository,
                                      AuthorizationErrorLog errorLog,
                                      Clock clock) {
        this.cardXrefRepository = cardXrefRepository;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.summaryRepository = summaryRepository;
        this.detailRepository = detailRepository;
        this.errorLog = errorLog;
        this.clock = clock;
    }

    /**
     * Processes one request message and returns the {@code CCPAURLY.cpy} reply
     * buffer, trailing comma included (COPAUA0C.cbl:721-730).
     */
    @Transactional
    public synchronized String process(String requestText) {
        AuthorizationRequest request = AuthorizationRequest.parse(requestText);

        Optional<CardXrefRecord> xref = cardXrefRepository.findById(request.cardNum());
        if (xref.isEmpty()) {
            errorLog.write(new AuthorizationErrorLog.Entry("A001",
                    AuthorizationErrorLog.Level.WARNING, AuthorizationErrorLog.Subsystem.APP,
                    "", "", PendingAuthMessages.CARD_NOT_FOUND_IN_XREF, request.cardNum()));
            return reply(request, false, "3100", BigDecimal.ZERO);
        }

        Long acctId = xref.get().getAcctId();
        Long custId = xref.get().getCustId();
        Optional<AccountRecord> account = accountRepository.findById(acctId);
        if (account.isEmpty()) {
            errorLog.write(new AuthorizationErrorLog.Entry("A002",
                    AuthorizationErrorLog.Level.WARNING, AuthorizationErrorLog.Subsystem.APP,
                    "", "", PendingAuthMessages.ACCT_NOT_FOUND_IN_XREF,
                    PendingAuthFormat.acctId11(acctId)));
        }
        boolean customerFound = customerRepository.findById(custId).isPresent();
        if (!customerFound) {
            errorLog.write(new AuthorizationErrorLog.Entry("A003",
                    AuthorizationErrorLog.Level.WARNING, AuthorizationErrorLog.Subsystem.APP,
                    "", "", PendingAuthMessages.CUST_NOT_FOUND_IN_XREF,
                    PendingAuthFormat.custId9(custId)));
        }

        Optional<PendingAuthSummaryRecord> summary = summaryRepository.findById(acctId);

        // 6000-MAKE-DECISION
        boolean declined;
        boolean insufficientFund = false;
        if (summary.isPresent()) {
            BigDecimal available = nz(summary.get().getPaCreditLimit())
                    .subtract(nz(summary.get().getPaCreditBalance()));
            insufficientFund = request.transactionAmt().compareTo(available) > 0;
            declined = insufficientFund;
        } else if (account.isPresent()) {
            BigDecimal available = nz(account.get().getCreditLimit())
                    .subtract(nz(account.get().getCurrBal()));
            insufficientFund = request.transactionAmt().compareTo(available) > 0;
            declined = insufficientFund;
        } else {
            declined = true;
        }

        String reason = "0000";
        if (declined) {
            if (account.isEmpty() || !customerFound) {
                reason = "3100";
            } else if (insufficientFund) {
                reason = "4100";
            } else {
                reason = "9000";
            }
        }
        BigDecimal approvedAmt = declined ? BigDecimal.ZERO : request.transactionAmt();
        String replyBuffer = reply(request, !declined, reason, approvedAmt);

        // 8000-WRITE-AUTH-TO-DB
        PendingAuthSummaryRecord summaryRow = summary.orElseGet(() -> {
            PendingAuthSummaryRecord fresh = new PendingAuthSummaryRecord();
            fresh.setPaAcctId(acctId);
            fresh.setPaCustId(custId);
            // INITIALIZE PENDING-AUTH-SUMMARY (COPAUA0C.cbl:802) leaves the
            // alphanumeric status fields as spaces; only the keys, the limits
            // and the counters are moved in.
            fresh.setPaAuthStatus(" ");
            fresh.setPaAccountStatus1("  ");
            fresh.setPaAccountStatus2("  ");
            fresh.setPaAccountStatus3("  ");
            fresh.setPaAccountStatus4("  ");
            fresh.setPaAccountStatus5("  ");
            fresh.setPaApprovedAuthCnt(0);
            fresh.setPaDeclinedAuthCnt(0);
            fresh.setPaApprovedAuthAmt(BigDecimal.ZERO);
            fresh.setPaDeclinedAuthAmt(BigDecimal.ZERO);
            fresh.setPaCreditBalance(BigDecimal.ZERO);
            fresh.setPaCashBalance(BigDecimal.ZERO);
            return fresh;
        });
        summaryRow.setPaCreditLimit(account.map(AccountRecord::getCreditLimit).orElse(BigDecimal.ZERO));
        summaryRow.setPaCashLimit(account.map(AccountRecord::getCashCreditLimit).orElse(BigDecimal.ZERO));
        if (declined) {
            summaryRow.setPaDeclinedAuthCnt(nz(summaryRow.getPaDeclinedAuthCnt()) + 1);
            summaryRow.setPaDeclinedAuthAmt(
                    nz(summaryRow.getPaDeclinedAuthAmt()).add(detailWorkAreaTransactionAmt));
        } else {
            summaryRow.setPaApprovedAuthCnt(nz(summaryRow.getPaApprovedAuthCnt()) + 1);
            summaryRow.setPaApprovedAuthAmt(nz(summaryRow.getPaApprovedAuthAmt()).add(approvedAmt));
            summaryRow.setPaCreditBalance(nz(summaryRow.getPaCreditBalance()).add(approvedAmt));
            summaryRow.setPaCashBalance(BigDecimal.ZERO);
        }
        summaryRepository.save(summaryRow);

        detailRepository.save(detail(request, acctId, declined, reason, approvedAmt));
        detailWorkAreaTransactionAmt = request.transactionAmt();
        return replyBuffer;
    }

    /** 8500-INSERT-AUTH: the new {@code PAUTDTL1} segment under the summary. */
    private PendingAuthDetailRecord detail(AuthorizationRequest request, Long acctId,
                                           boolean declined, String reason,
                                           BigDecimal approvedAmt) {
        PendingAuthDetailId id = PendingAuthKey.newKey(acctId, LocalDateTime.now(clock));
        PendingAuthDetailRecord d = new PendingAuthDetailRecord();
        d.setId(id);
        d.setPaAuthOrigDate(request.authDate());
        d.setPaAuthOrigTime(request.authTime());
        d.setPaCardNum(request.cardNum());
        d.setPaAuthType(request.authType());
        d.setPaCardExpiryDate(request.cardExpiryDate());
        d.setPaMessageType(request.messageType());
        d.setPaMessageSource(request.messageSource());
        d.setPaProcessingCode(digits(request.processingCode()));
        d.setPaTransactionAmt(request.transactionAmt());
        d.setPaMerchantCatagoryCode(request.merchantCategoryCode());
        d.setPaAcqrCountryCode(request.acqrCountryCode());
        d.setPaPosEntryMode(digits(request.posEntryMode()) == null
                ? null : digits(request.posEntryMode()).intValue());
        d.setPaMerchantId(request.merchantId());
        d.setPaMerchantName(request.merchantName());
        d.setPaMerchantCity(request.merchantCity());
        d.setPaMerchantState(request.merchantState());
        d.setPaMerchantZip(request.merchantZip());
        d.setPaTransactionId(request.transactionId());
        // PA-RL-AUTH-ID-CODE is the request's auth time (6000-MAKE-DECISION).
        d.setPaAuthIdCode(request.authTime());
        d.setPaAuthRespCode(declined ? "05" : "00");
        d.setPaAuthRespReason(reason);
        d.setPaApprovedAmt(approvedAmt);
        d.setPaMatchStatus(declined ? "D" : "P");
        d.setPaAuthFraud(" ");
        d.setPaFraudRptDate(" ");
        return d;
    }

    /**
     * The {@code CCPAURLY.cpy} reply: card, transaction id, auth id code,
     * response code, reason and the edited approved amount, each followed by a
     * comma — the STRING leaves a trailing comma (quirk Q-9).
     */
    private String reply(AuthorizationRequest request, boolean approved, String reason,
                         BigDecimal approvedAmt) {
        return pad(request.cardNum(), 16) + ','
                + pad(request.transactionId(), 15) + ','
                + pad(request.authTime(), 6) + ','
                + (approved ? "00" : "05") + ','
                + reason + ','
                + PendingAuthFormat.amount14(approvedAmt) + ',';
    }

    private static Long digits(String value) {
        String s = value == null ? "" : value.trim();
        if (s.isEmpty() || !s.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return Long.valueOf(s);
    }

    private static String pad(String value, int width) {
        String v = value == null ? "" : value;
        return v.length() >= width ? v.substring(0, width) : v + " ".repeat(width - v.length());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
