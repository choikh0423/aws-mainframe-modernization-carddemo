package com.carddemo.billpayment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.billpayment.dto.BillPaymentRequest;
import com.carddemo.billpayment.dto.BillPaymentScreen;
import com.carddemo.billpayment.entity.Transaction;
import com.carddemo.billpayment.repository.AccountRepository;
import com.carddemo.billpayment.repository.TransactionRepository;
import com.carddemo.billpayment.util.CobolRecords;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BillPaymentServiceTest {

    @Autowired BillPaymentService service;
    @Autowired SeedDataLoader seed;
    @Autowired AccountRepository accounts;
    @Autowired TransactionRepository transactions;

    @BeforeEach
    void reseed() {
        seed.reload();
    }

    private BillPaymentScreen enter(String acct, String bal, String confirm) {
        return service.handle(new BillPaymentRequest("ENTER", acct, bal, confirm, "COMEN01C"));
    }

    @Test
    void emptyAccountId() {
        BillPaymentScreen s = enter("", "", "");
        assertThat(s.message()).isEqualTo("Acct ID can NOT be empty...");
        assertThat(s.messageColor()).isEqualTo("RED");
        assertThat(s.cursor()).isEqualTo("ACTIDIN");
    }

    @Test
    void unknownAccount() {
        BillPaymentScreen s = enter("99999999999", "", "");
        assertThat(s.message()).isEqualTo("Account ID NOT found...");
        assertThat(s.acctId()).isEqualTo("99999999999");
    }

    @Test
    void shortAccountIdIsAnExactKeyLikeEnterpriseCobol() {
        assertThat(enter("1", "", "").message()).isEqualTo("Account ID NOT found...");
    }

    @Test
    void lookupShowsBalanceAndAsksForConfirmation() {
        BillPaymentScreen s = enter("00000000001", "", "");
        assertThat(s.currentBalance()).isEqualTo("+0000000194.00");
        assertThat(s.message()).isEqualTo("Confirm to make a bill payment...");
        assertThat(s.cursor()).isEqualTo("CONFIRM");
    }

    @Test
    void invalidConfirmationKeepsScreenValues() {
        BillPaymentScreen s = enter("00000000001", "+0000000194.00", "X");
        assertThat(s.message()).isEqualTo("Invalid value. Valid values are (Y/N)...");
        assertThat(s.currentBalance()).isEqualTo("+0000000194.00");
        assertThat(s.cursor()).isEqualTo("CONFIRM");
    }

    @Test
    void noClearsTheScreen() {
        BillPaymentScreen s = enter("00000000001", "+0000000194.00", "n");
        assertThat(s.acctId()).isEmpty();
        assertThat(s.currentBalance()).isEmpty();
        assertThat(s.message()).isEmpty();
    }

    @Test
    void yesPaysInFullAndWritesTransaction() {
        BillPaymentScreen s = enter("00000000001", "+0000000194.00", "Y");
        assertThat(s.message()).isEqualTo("Payment successful.  Your Transaction ID is 0000000996722788.");
        assertThat(s.messageColor()).isEqualTo("GREEN");
        assertThat(s.acctId()).isEmpty();
        assertThat(accounts.findById("00000000001").orElseThrow().getCurrBal())
                .isEqualByComparingTo(BigDecimal.ZERO);
        Transaction t = transactions.findById("0000000996722788").orElseThrow();
        String rec = CobolRecords.formatTransaction(t);
        assertThat(rec).hasSize(350);
        assertThat(rec.substring(0, 143)).isEqualTo("0000000996722788" + "02" + "0002"
                + CobolRecords.pad("POS TERM", 10) + CobolRecords.pad("BILL PAYMENT - ONLINE", 100)
                + "0000001940{");
        assertThat(rec.substring(143, 278)).isEqualTo("999999999" + CobolRecords.pad("BILL PAYMENT", 50)
                + CobolRecords.pad("N/A", 50) + CobolRecords.pad("N/A", 10) + "9680294154603697");
        assertThat(t.getOrigTs()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.000000");
    }

    @Test
    void secondPaymentHasNothingToPay() {
        enter("00000000001", "", "y");
        BillPaymentScreen s = enter("00000000001", "", "");
        assertThat(s.message()).isEqualTo("You have nothing to pay...");
        assertThat(s.currentBalance()).isEqualTo("+0000000000.00");
    }

    @Test
    void functionKeys() {
        BillPaymentScreen back = service.handle(new BillPaymentRequest("PF3", "1", "", "", "COMEN01C"));
        assertThat(back.nextProgram()).isEqualTo("COMEN01C");
        BillPaymentScreen noFrom = service.handle(new BillPaymentRequest("PF3", "", "", "", " "));
        assertThat(noFrom.nextProgram()).isEqualTo("COMEN01C");
        BillPaymentScreen clear = service.handle(new BillPaymentRequest("PF4", "00000000001", "x", "Y", null));
        assertThat(clear.acctId()).isEmpty();
        assertThat(clear.message()).isEmpty();
        BillPaymentScreen other = service.handle(new BillPaymentRequest("PF5", "", "", "", null));
        assertThat(other.message()).isEqualTo("Invalid key pressed. Please see below...");
    }

    @Test
    void balanceFormatMatchesPicture() {
        assertThat(BillPaymentService.formatBalance(new BigDecimal("-12.3"))).isEqualTo("-0000000012.30");
    }
}
