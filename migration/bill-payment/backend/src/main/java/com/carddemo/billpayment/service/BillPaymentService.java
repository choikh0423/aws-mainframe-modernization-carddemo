package com.carddemo.billpayment.service;

import com.carddemo.billpayment.dto.BillPaymentRequest;
import com.carddemo.billpayment.dto.BillPaymentScreen;
import com.carddemo.billpayment.entity.Account;
import com.carddemo.billpayment.entity.CardXref;
import com.carddemo.billpayment.entity.Transaction;
import com.carddemo.billpayment.repository.AccountRepository;
import com.carddemo.billpayment.repository.CardXrefRepository;
import com.carddemo.billpayment.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** COBIL00C (transaction CB00): pay the account balance in full. */
@Service
public class BillPaymentService {

    public static final String TRAN_ID = "CB00";
    public static final String PROGRAM = "COBIL00C";
    public static final String MENU_PROGRAM = "COMEN01C";

    static final String MSG_ACCT_EMPTY = "Acct ID can NOT be empty...";
    static final String MSG_ACCT_NOT_FOUND = "Account ID NOT found...";
    static final String MSG_INVALID_CONFIRM = "Invalid value. Valid values are (Y/N)...";
    static final String MSG_NOTHING_TO_PAY = "You have nothing to pay...";
    static final String MSG_CONFIRM = "Confirm to make a bill payment...";
    static final String MSG_TRAN_EXISTS = "Tran ID already exist...";
    static final String MSG_INVALID_KEY = "Invalid key pressed. Please see below...";
    static final String MSG_SUCCESS_PREFIX = "Payment successful.  Your Transaction ID is ";

    static final String FIELD_ACCT = "ACTIDIN";
    static final String FIELD_CONFIRM = "CONFIRM";

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AccountRepository accounts;
    private final CardXrefRepository xrefs;
    private final TransactionRepository transactions;
    private final Clock clock;

    public BillPaymentService(AccountRepository accounts, CardXrefRepository xrefs,
                              TransactionRepository transactions, Optional<Clock> clock) {
        this.accounts = accounts;
        this.xrefs = xrefs;
        this.transactions = transactions;
        this.clock = clock.orElse(Clock.systemDefaultZone());
    }

    /** First entry from the menu (COMMAREA present, not re-entered): an empty screen. */
    public BillPaymentScreen initialScreen() {
        return new Screen(null, null, null).render(this);
    }

    @Transactional
    public BillPaymentScreen handle(BillPaymentRequest req) {
        Screen s = new Screen(field(req.acctId(), 11), field(req.currentBalance(), 14),
                field(req.confirm(), 1));
        String aid = req.aid() == null ? "ENTER" : req.aid().toUpperCase();
        switch (aid) {
            case "ENTER" -> processEnterKey(s);
            case "PF3" -> s.nextProgram = isBlank(req.fromProgram()) ? MENU_PROGRAM
                    : req.fromProgram().strip();
            case "PF4" -> s.clear();
            default -> s.message = MSG_INVALID_KEY;
        }
        return s.render(this);
    }

    private void processEnterKey(Screen s) {
        if (isBlank(s.acctId)) {
            s.error(MSG_ACCT_EMPTY, FIELD_ACCT);
            return;
        }
        boolean payNow;
        String confirm = s.confirm == null ? "" : s.confirm;
        switch (confirm) {
            case "Y", "y" -> payNow = true;
            case "N", "n" -> {
                s.clear();
                return;
            }
            case "", " " -> payNow = false;
            default -> {
                s.error(MSG_INVALID_CONFIRM, FIELD_CONFIRM);
                return;
            }
        }
        // The key is the 11 characters typed into ACTIDIN, exactly as RIDFLD(ACCT-ID).
        Optional<Account> found = accounts.findForUpdate(CobolKey.pad(s.acctId, 11));
        if (found.isEmpty()) {
            s.error(MSG_ACCT_NOT_FOUND, FIELD_ACCT);
            return;
        }
        Account account = found.get();
        s.currentBalance = formatBalance(account.getCurrBal());
        if (account.getCurrBal().signum() <= 0) {
            s.error(MSG_NOTHING_TO_PAY, FIELD_ACCT);
            return;
        }
        if (!payNow) {
            s.message = MSG_CONFIRM;
            s.cursor = FIELD_CONFIRM;
            return;
        }
        Optional<CardXref> xref = xrefs.findFirstByAcctIdOrderByCardNumAsc(account.getAcctId());
        if (xref.isEmpty()) {
            s.error(MSG_ACCT_NOT_FOUND, FIELD_ACCT);
            return;
        }
        String newTranId = nextTranId();
        if (transactions.existsById(newTranId)) {
            s.error(MSG_TRAN_EXISTS, FIELD_ACCT);
            return;
        }
        BigDecimal amount = account.getCurrBal();
        String ts = LocalDateTime.now(clock).format(TS) + ".000000";
        Transaction t = new Transaction();
        t.setTranId(newTranId);
        t.setTypeCd("02");
        t.setCatCd(2);
        t.setSource("POS TERM");
        t.setDescription("BILL PAYMENT - ONLINE");
        t.setAmount(amount);
        t.setCardNum(xref.get().getCardNum());
        t.setMerchantId(999999999L);
        t.setMerchantName("BILL PAYMENT");
        t.setMerchantCity("N/A");
        t.setMerchantZip("N/A");
        t.setOrigTs(ts);
        t.setProcTs(ts);
        transactions.save(t);
        account.setCurrBal(account.getCurrBal().subtract(amount));
        accounts.save(account);
        s.clear();
        s.message = MSG_SUCCESS_PREFIX + newTranId + ".";
        s.messageColor = "GREEN";
        s.transactionId = newTranId;
    }

    /** Highest TRAN-ID on TRANSACT + 1, as a 16-digit key (WS-TRAN-ID-NUM PIC 9(16)). */
    String nextTranId() {
        BigInteger last = transactions.findTopByOrderByTranIdDesc()
                .map(t -> new BigInteger(t.getTranId().strip()))
                .orElse(BigInteger.ZERO);
        String next = last.add(BigInteger.ONE).toString();
        return "0".repeat(Math.max(0, 16 - next.length())) + next;
    }

    /** ACCT-CURR-BAL moved to WS-CURR-BAL PIC +9999999999.99. */
    public static String formatBalance(BigDecimal value) {
        String digits = String.format("%013.2f", value.abs());
        return (value.signum() < 0 ? "-" : "+") + digits;
    }

    private static String field(String value, int length) {
        if (value == null) {
            return null;
        }
        return value.length() > length ? value.substring(0, length) : value;
    }

    private static boolean isBlank(String v) {
        return v == null || v.replace('\0', ' ').isBlank();
    }

    String date() {
        return LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("MM/dd/yy"));
    }

    String time() {
        return LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    /** Mutable screen state while the request is processed (COBIL0AO). */
    private static final class Screen {
        String acctId;
        String currentBalance;
        String confirm;
        String message = "";
        String messageColor = "RED";
        String cursor = FIELD_ACCT;
        String nextProgram;
        String transactionId;

        Screen(String acctId, String currentBalance, String confirm) {
            this.acctId = acctId;
            this.currentBalance = currentBalance;
            this.confirm = confirm;
        }

        void error(String msg, String field) {
            message = msg;
            cursor = field;
        }

        void clear() {
            acctId = "";
            currentBalance = "";
            confirm = "";
            message = "";
            cursor = FIELD_ACCT;
        }

        BillPaymentScreen render(BillPaymentService svc) {
            return new BillPaymentScreen(TRAN_ID, PROGRAM, svc.date(), svc.time(),
                    nz(acctId), nz(currentBalance), nz(confirm), message, messageColor,
                    cursor, nextProgram, transactionId);
        }

        private static String nz(String v) {
            return v == null ? "" : v.stripTrailing();
        }
    }

    /** BMS pads input fields with blanks on the right. */
    static final class CobolKey {
        private CobolKey() {
        }

        static String pad(String v, int len) {
            return v.length() >= len ? v.substring(0, len) : v + " ".repeat(len - v.length());
        }
    }
}
