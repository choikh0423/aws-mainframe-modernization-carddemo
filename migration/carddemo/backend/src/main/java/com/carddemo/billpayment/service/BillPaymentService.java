package com.carddemo.billpayment.service;

import com.carddemo.billpayment.BillPaymentFormat;
import com.carddemo.billpayment.BillPaymentMessages;
import com.carddemo.billpayment.dto.BillPaymentRequest;
import com.carddemo.billpayment.dto.BillPaymentResponse;
import com.carddemo.billpayment.exception.AccountNotFoundException;
import com.carddemo.billpayment.exception.BillPaymentValidationException;
import com.carddemo.billpayment.exception.DuplicateTranIdException;
import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.TransactionRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.CardXrefRepository;
import com.carddemo.common.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * S-06 Bill Payment (COBIL00C, transaction CB00) — a 1:1 port of
 * PROCESS-ENTER-KEY (app/cbl/COBIL00C.cbl:154-244) and the file paragraphs it
 * performs.
 *
 * <p>One call is one ENTER turn of the pseudo-conversational screen:
 * <ol>
 *   <li>blank Acct ID -&gt; "Acct ID can NOT be empty..." (cbl:158-164);</li>
 *   <li>Confirm edit on the raw character: Y/y pays, N/n blanks the screen
 *       silently, blank is a balance inquiry, anything else is rejected
 *       (cbl:173-191);</li>
 *   <li>READ ACCTDAT for update, display the balance, refuse a non-positive one
 *       with "You have nothing to pay..." (cbl:177-206);</li>
 *   <li>on Y: READ CXACAIX for the card, take the highest TRANSACT key + 1,
 *       WRITE the payment and REWRITE the account with the reduced balance
 *       (cbl:208-235).</li>
 * </ol>
 *
 * <p>The whole payment is one unit of work, matching the implicit CICS syncpoint
 * that covered the WRITE and the REWRITE.
 */
@Service
public class BillPaymentService {

    /** Fixed record content COBIL00C moves into TRAN-RECORD (cbl:218-229). */
    private static final String TRAN_TYPE_CD = "02";
    private static final int TRAN_CAT_CD = 2;
    private static final String TRAN_SOURCE = "POS TERM";
    private static final String TRAN_DESC = "BILL PAYMENT - ONLINE";
    private static final long MERCHANT_ID = 999999999L;
    private static final String MERCHANT_NAME = "BILL PAYMENT";
    private static final String MERCHANT_CITY = "N/A";
    private static final String MERCHANT_ZIP = "N/A";

    private final AccountRepository accountRepository;
    private final CardXrefRepository cardXrefRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    @Autowired
    public BillPaymentService(AccountRepository accountRepository,
                              CardXrefRepository cardXrefRepository,
                              TransactionRepository transactionRepository) {
        this(accountRepository, cardXrefRepository, transactionRepository, Clock.systemDefaultZone());
    }

    /** Test seam for GET-CURRENT-TIMESTAMP (cbl:249-267). */
    BillPaymentService(AccountRepository accountRepository,
                       CardXrefRepository cardXrefRepository,
                       TransactionRepository transactionRepository,
                       Clock clock) {
        this.accountRepository = accountRepository;
        this.cardXrefRepository = cardXrefRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    /** One ENTER turn of map COBIL0A. */
    @Transactional
    public BillPaymentResponse enter(BillPaymentRequest request) {
        String accountId = request.getAccountId() == null ? "" : request.getAccountId().trim();
        String confirm = request.getConfirm() == null ? "" : request.getConfirm().trim();

        // cbl:158-167 — the only edit applied before any file is touched.
        if (accountId.isEmpty()) {
            throw new BillPaymentValidationException(BillPaymentMessages.ACCT_ID_EMPTY);
        }

        // cbl:173-191 — EVALUATE CONFIRMI, case-exact on the single character.
        switch (confirm) {
            case "N":
            case "n":
                // CLEAR-CURRENT-SCREEN then WS-ERR-FLG = 'Y': no message, no read (quirk Q-7).
                return BillPaymentResponse.cleared();
            case "Y":
            case "y":
            case "":
                break;
            default:
                throw new BillPaymentValidationException(BillPaymentMessages.INVALID_CONFIRM);
        }

        AccountRecord account = readAccount(accountId);
        BigDecimal balance = account.getCurrBal();
        String balanceEdited = BillPaymentFormat.currBalEdited(balance);

        // cbl:197-206 — the balance is on the map before this check runs.
        if (balance.signum() <= 0) {
            return BillPaymentResponse.nothingToPay(accountId, balanceEdited);
        }

        if (confirm.isEmpty()) {
            // cbl:236-239 — balance inquiry: nothing is written.
            return BillPaymentResponse.confirmPrompt(accountId, balanceEdited);
        }

        return pay(account, balance);
    }

    /** READ-ACCTDAT-FILE (cbl:343-372); a key that is not 9(11)-compatible cannot match (quirk Q-4). */
    private AccountRecord readAccount(String accountId) {
        long key;
        try {
            key = Long.parseLong(accountId);
        } catch (NumberFormatException e) {
            throw new AccountNotFoundException();
        }
        return accountRepository.findById(key).orElseThrow(AccountNotFoundException::new);
    }

    /** cbl:210-235 — the confirmed payment. */
    private BillPaymentResponse pay(AccountRecord account, BigDecimal balance) {
        // READ-CXACAIX-FILE (cbl:408-436): the card the payment is posted against.
        CardXrefRecord xref = cardXrefRepository.findByAcctId(account.getAcctId())
                .orElseThrow(AccountNotFoundException::new);

        // STARTBR HIGH-VALUES + READPREV + 1 (cbl:212-217); an empty file yields 1 (cbl:487-488).
        long maxKey = transactionRepository.findTopByOrderByIdDesc()
                .map(record -> Long.parseLong(record.getId().trim()))
                .orElse(0L);
        String tranId = BillPaymentFormat.tranId(maxKey + 1);
        if (transactionRepository.existsById(tranId)) {
            throw new DuplicateTranIdException();
        }

        BigDecimal amount = BillPaymentFormat.tranAmount(balance);
        transactionRepository.save(buildPayment(tranId, amount, xref.getCardNum()));

        // COMPUTE ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT, then REWRITE (cbl:234-235).
        account.setCurrBal(balance.subtract(amount));
        accountRepository.save(account);

        return BillPaymentResponse.paid(tranId);
    }

    /** INITIALIZE TRAN-RECORD + the literal MOVEs (cbl:218-232). */
    private TransactionRecord buildPayment(String tranId, BigDecimal amount, String cardNum) {
        String timestamp = BillPaymentFormat.timestamp(LocalDateTime.now(clock));
        TransactionRecord payment = new TransactionRecord();
        payment.setId(tranId);
        payment.setTypeCd(TRAN_TYPE_CD);
        payment.setCatCd(TRAN_CAT_CD);
        payment.setSource(TRAN_SOURCE);
        payment.setDescription(TRAN_DESC);
        payment.setAmount(amount);
        payment.setCardNum(cardNum);
        payment.setMerchantId(MERCHANT_ID);
        payment.setMerchantName(MERCHANT_NAME);
        payment.setMerchantCity(MERCHANT_CITY);
        payment.setMerchantZip(MERCHANT_ZIP);
        payment.setOrigTs(timestamp);
        payment.setProcTs(timestamp);
        return payment;
    }
}
