package com.carddemo.billpayment.service;

import com.carddemo.billpayment.dto.BillPaymentRequest;
import com.carddemo.billpayment.dto.BillPaymentResponse;
import com.carddemo.billpayment.exception.AccountNotFoundException;
import com.carddemo.billpayment.exception.BillPaymentValidationException;
import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.TransactionRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-BP-10..FR-BP-24 and the end-to-end payment (FR-BP-38/39) against the seeded
 * H2 database: account 1 has a 194.00 balance and a card_xref row
 * (app/data/ASCII/acctdata.txt + cardxref.txt), and the CT02 demo seed leaves 30
 * transactions, so the next Bill Payment id is 31.
 *
 * <p>{@code @Transactional} rolls each test back so the shared in-memory DB stays
 * as seeded for the rest of the suite.
 */
@SpringBootTest
@Transactional
class BillPaymentServiceTest {

    private static final long SEEDED_ACCOUNT = 1L;

    @Autowired
    private BillPaymentService service;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private static BillPaymentRequest request(String accountId, String confirm) {
        BillPaymentRequest request = new BillPaymentRequest();
        request.setAccountId(accountId);
        request.setConfirm(confirm);
        return request;
    }

    @Test
    void frBp10_emptyAccountId() {
        assertThatThrownBy(() -> service.enter(request("", "")))
                .isInstanceOf(BillPaymentValidationException.class)
                .hasMessage("Acct ID can NOT be empty...");
        assertThatThrownBy(() -> service.enter(request("   ", "Y")))
                .isInstanceOf(BillPaymentValidationException.class)
                .hasMessage("Acct ID can NOT be empty...");
        assertThatThrownBy(() -> service.enter(request(null, null)))
                .isInstanceOf(BillPaymentValidationException.class)
                .hasMessage("Acct ID can NOT be empty...");
    }

    @Test
    void frBp11_confirmCharacterEvaluation() {
        // Blank enquires; N and n clear; anything but Y/y/N/n is rejected; y pays.
        assertThat(service.enter(request("1", "")).getMessage())
                .isEqualTo("Confirm to make a bill payment...");
        assertThat(service.enter(request("1", "n")).getMessage()).isEmpty();
        assertThat(service.enter(request("1", "N")).getMessage()).isEmpty();
        assertThatThrownBy(() -> service.enter(request("1", "X")))
                .isInstanceOf(BillPaymentValidationException.class)
                .hasMessage("Invalid value. Valid values are (Y/N)...");
        assertThat(service.enter(request("1", "y")).getTranId()).isEqualTo("0000000000000031");
    }

    @Test
    void frBp12_confirmNo_clearsScreenSilently() {
        BigDecimal before = accountRepository.findById(SEEDED_ACCOUNT).orElseThrow().getCurrBal();
        long rowsBefore = transactionRepository.count();

        BillPaymentResponse response = service.enter(request("1", "N"));

        assertThat(response.getAccountId()).isEmpty();
        assertThat(response.getCurrentBalance()).isEmpty();
        assertThat(response.getConfirm()).isEmpty();
        assertThat(response.getMessage()).isEmpty();
        assertThat(response.isError()).isFalse();
        assertThat(transactionRepository.count()).isEqualTo(rowsBefore);
        assertThat(accountRepository.findById(SEEDED_ACCOUNT).orElseThrow().getCurrBal())
                .isEqualByComparingTo(before);
    }

    @Test
    void frBp13_confirmInvalid() {
        // The account is never read on this turn, so no balance reaches the screen (quirk Q-2).
        assertThatThrownBy(() -> service.enter(request("1", "?")))
                .isInstanceOf(BillPaymentValidationException.class)
                .hasMessage("Invalid value. Valid values are (Y/N)...");
    }

    @Test
    void frBp14_nonNumericAccountId() {
        assertThatThrownBy(() -> service.enter(request("ABCDEFGHIJK", "")))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account ID NOT found...");
    }

    @Test
    void frBp21_accountNotFound() {
        assertThatThrownBy(() -> service.enter(request("99999999999", "")))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account ID NOT found...");
    }

    @Test
    void frBp23_nothingToPay_stillShowsTheBalance() {
        AccountRecord account = accountRepository.findById(SEEDED_ACCOUNT).orElseThrow();
        account.setCurrBal(new BigDecimal("0.00"));
        accountRepository.saveAndFlush(account);

        BillPaymentResponse response = service.enter(request("1", "Y"));

        assertThat(response.getMessage()).isEqualTo("You have nothing to pay...");
        assertThat(response.isError()).isTrue();
        assertThat(response.getCurrentBalance()).isEqualTo("+0000000000.00");
        assertThat(response.getCursor()).isEqualTo(BillPaymentResponse.CURSOR_ACCT_ID);

        account.setCurrBal(new BigDecimal("-12.34"));
        accountRepository.saveAndFlush(account);
        assertThat(service.enter(request("1", "Y")).getCurrentBalance()).isEqualTo("-0000000012.34");
    }

    @Test
    void frBp24_balanceInquiry_showsBalanceAndWritesNothing() {
        long rowsBefore = transactionRepository.count();

        BillPaymentResponse response = service.enter(request("1", ""));

        assertThat(response.getAccountId()).isEqualTo("1");
        assertThat(response.getCurrentBalance()).isEqualTo("+0000000194.00");
        assertThat(response.getMessage()).isEqualTo("Confirm to make a bill payment...");
        assertThat(response.getCursor()).isEqualTo(BillPaymentResponse.CURSOR_CONFIRM);
        assertThat(response.isError()).isFalse();
        assertThat(transactionRepository.count()).isEqualTo(rowsBefore);
        assertThat(accountRepository.findById(SEEDED_ACCOUNT).orElseThrow().getCurrBal())
                .isEqualByComparingTo("194.00");
    }

    @Test
    void frBp38_paymentWritesTheRowAndZeroesTheBalance() {
        BillPaymentResponse response = service.enter(request("1", "Y"));

        assertThat(response.getTranId()).isEqualTo("0000000000000031");
        assertThat(response.getMessage())
                .isEqualTo("Payment successful.  Your Transaction ID is 0000000000000031.");

        TransactionRecord written = transactionRepository.findById("0000000000000031").orElseThrow();
        assertThat(written.getAmount()).isEqualByComparingTo("194.00");
        assertThat(written.getCardNum()).isEqualTo("9680294154603697");
        assertThat(written.getDescription()).isEqualTo("BILL PAYMENT - ONLINE");
        assertThat(accountRepository.findById(SEEDED_ACCOUNT).orElseThrow().getCurrBal())
                .isEqualByComparingTo("0.00");
    }
}
