package com.carddemo.billpayment.service;

import com.carddemo.billpayment.dto.BillPaymentRequest;
import com.carddemo.billpayment.dto.BillPaymentResponse;
import com.carddemo.billpayment.exception.AccountNotFoundException;
import com.carddemo.billpayment.exception.DuplicateTranIdException;
import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.TransactionRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.CardXrefRepository;
import com.carddemo.common.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FR-BP-30..FR-BP-40 — the confirmed-payment path of COBIL00C
 * (app/cbl/COBIL00C.cbl:208-235) exercised against mocked files, so the exact
 * record content, the key generation, the truncating amount MOVE and the
 * timestamp can be asserted without seed data.
 */
class BillPaymentServiceMockTest {

    private static final String CARD = "9680294154603697";

    private AccountRepository accounts;
    private CardXrefRepository xrefs;
    private TransactionRepository transactions;
    private BillPaymentService service;

    private final Clock clock = Clock.fixed(Instant.parse("2026-02-03T04:05:06.789Z"), ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        xrefs = mock(CardXrefRepository.class);
        transactions = mock(TransactionRepository.class);
        service = new BillPaymentService(accounts, xrefs, transactions, clock);
    }

    private static BillPaymentRequest pay(String accountId) {
        BillPaymentRequest request = new BillPaymentRequest();
        request.setAccountId(accountId);
        request.setConfirm("Y");
        return request;
    }

    private AccountRecord account(long id, String balance) {
        AccountRecord account = new AccountRecord();
        account.setAcctId(id);
        account.setCurrBal(new BigDecimal(balance));
        when(accounts.findById(id)).thenReturn(Optional.of(account));
        return account;
    }

    private void xref(long acctId) {
        CardXrefRecord xref = new CardXrefRecord();
        xref.setAcctId(acctId);
        xref.setCardNum(CARD);
        when(xrefs.findByAcctId(acctId)).thenReturn(Optional.of(xref));
    }

    private void highestTranId(String id) {
        if (id == null) {
            when(transactions.findTopByOrderByIdDesc()).thenReturn(Optional.empty());
            return;
        }
        TransactionRecord last = new TransactionRecord();
        last.setId(id);
        when(transactions.findTopByOrderByIdDesc()).thenReturn(Optional.of(last));
    }

    private TransactionRecord written() {
        ArgumentCaptor<TransactionRecord> captor = ArgumentCaptor.forClass(TransactionRecord.class);
        verify(transactions).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void frBp30_resolvesCardFromXref() {
        account(1L, "194.00");
        xref(1L);
        highestTranId("0000000000000030");

        service.enter(pay("1"));

        assertThat(written().getCardNum()).isEqualTo(CARD);
    }

    @Test
    void frBp31_xrefMissing_reportsAccountNotFoundAndWritesNothing() {
        account(1L, "194.00");
        when(xrefs.findByAcctId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enter(pay("1")))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account ID NOT found...");
        verify(transactions, never()).save(any());
        verify(accounts, never()).save(any());
    }

    @Test
    void frBp32_tranIdIsMaxPlusOne() {
        account(1L, "194.00");
        xref(1L);
        highestTranId("0000000000000030");

        BillPaymentResponse response = service.enter(pay("1"));

        assertThat(response.getTranId()).isEqualTo("0000000000000031");
        assertThat(written().getId()).isEqualTo("0000000000000031");
    }

    @Test
    void frBp33_emptyTransactFile_firstIdIsOne() {
        account(1L, "194.00");
        xref(1L);
        highestTranId(null);

        assertThat(service.enter(pay("1")).getTranId()).isEqualTo("0000000000000001");
    }

    @Test
    void frBp34_recordLiterals() {
        account(1L, "194.00");
        xref(1L);
        highestTranId("0000000000000030");

        service.enter(pay("1"));

        TransactionRecord record = written();
        assertThat(record.getTypeCd()).isEqualTo("02");
        assertThat(record.getCatCd()).isEqualTo(2);
        assertThat(record.getSource()).isEqualTo("POS TERM");
        assertThat(record.getDescription()).isEqualTo("BILL PAYMENT - ONLINE");
        assertThat(record.getMerchantId()).isEqualTo(999999999L);
        assertThat(record.getMerchantName()).isEqualTo("BILL PAYMENT");
        assertThat(record.getMerchantCity()).isEqualTo("N/A");
        assertThat(record.getMerchantZip()).isEqualTo("N/A");
        assertThat(record.getAmount()).isEqualByComparingTo("194.00");
    }

    @Test
    void frBp35_amountTruncatedToNineDigits() {
        AccountRecord account = account(1L, "1234567890.12");
        xref(1L);
        highestTranId("0000000000000030");

        service.enter(pay("1"));

        // MOVE S9(10)V99 -> S9(09)V99 drops the leading 1 (quirk Q-1) ...
        assertThat(written().getAmount()).isEqualByComparingTo("234567890.12");
        // ... so the "pay in full" screen leaves 1000000000.00 on the account.
        assertThat(account.getCurrBal()).isEqualByComparingTo("1000000000.00");
    }

    @Test
    void frBp36_timestampFormat() {
        account(1L, "194.00");
        xref(1L);
        highestTranId("0000000000000030");

        service.enter(pay("1"));

        TransactionRecord record = written();
        assertThat(record.getOrigTs()).isEqualTo("2026-02-03 04:05:06.000000");
        assertThat(record.getProcTs()).isEqualTo(record.getOrigTs());
    }

    @Test
    void frBp37_duplicateTranId() {
        account(1L, "194.00");
        xref(1L);
        highestTranId("0000000000000030");
        when(transactions.existsById("0000000000000031")).thenReturn(true);

        assertThatThrownBy(() -> service.enter(pay("1")))
                .isInstanceOf(DuplicateTranIdException.class)
                .hasMessage("Tran ID already exist...");
        verify(transactions, never()).save(any());
        verify(accounts, never()).save(any());
    }

    @Test
    void frBp38_balanceReducedByAmount() {
        AccountRecord account = account(1L, "194.00");
        xref(1L);
        highestTranId("0000000000000030");

        service.enter(pay("1"));

        assertThat(account.getCurrBal()).isEqualByComparingTo("0.00");
        assertThat(account.getCurrBal().scale()).isEqualTo(2);
        verify(accounts).save(account);
    }

    @Test
    void frBp39_successMessage() {
        account(1L, "194.00");
        xref(1L);
        highestTranId("0000000000000030");

        BillPaymentResponse response = service.enter(pay("1"));

        assertThat(response.getMessage())
                .isEqualTo("Payment successful.  Your Transaction ID is 0000000000000031.");
        assertThat(response.getMessageColour()).isEqualTo(BillPaymentResponse.COLOUR_GREEN);
        assertThat(response.getAccountId()).isEmpty();
        assertThat(response.getCurrentBalance()).isEmpty();
        assertThat(response.getConfirm()).isEmpty();
        assertThat(response.isError()).isFalse();
    }
}
