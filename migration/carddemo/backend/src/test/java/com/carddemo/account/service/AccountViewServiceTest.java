package com.carddemo.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carddemo.account.dto.AccountViewResponse;
import com.carddemo.account.exception.AccountFilterException;
import com.carddemo.account.exception.AccountNotFoundException;
import com.carddemo.account.validator.AccountFilterValidator;
import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.CustomerRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.CardXrefRepository;
import com.carddemo.common.repository.CustomerRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * CAVW read path: the CXACAIX -> ACCTDAT -> CUSTDAT order (FR-AV-02) and the message the
 * first failing read owns (FR-AV-06..FR-AV-08), plus the filter edits (FR-AV-03..FR-AV-05).
 */
@ExtendWith(MockitoExtension.class)
class AccountViewServiceTest {

    @Mock
    private CardXrefRepository cardXrefRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CustomerRepository customerRepository;

    private AccountViewService service() {
        return new AccountViewService(new AccountFilterValidator(),
                new AccountLookupService(cardXrefRepository, accountRepository, customerRepository));
    }

    private CardXrefRecord xref() {
        CardXrefRecord record = new CardXrefRecord();
        record.setCardNum("4111111111111111");
        record.setCustId(9L);
        record.setAcctId(1L);
        return record;
    }

    private AccountRecord account() {
        AccountRecord record = new AccountRecord();
        record.setAcctId(1L);
        record.setActiveStatus("Y");
        record.setCurrBal(new java.math.BigDecimal("-197.13"));
        record.setCreditLimit(new java.math.BigDecimal("5000.00"));
        record.setCashCreditLimit(new java.math.BigDecimal("500.00"));
        record.setCurrCycCredit(new java.math.BigDecimal("0.00"));
        record.setCurrCycDebit(new java.math.BigDecimal("0.00"));
        record.setOpenDate("2014-11-20");
        record.setExpiraionDate("2025-05-20");
        record.setReissueDate("2025-05-20");
        record.setGroupId("");
        return record;
    }

    private CustomerRecord customer() {
        CustomerRecord record = new CustomerRecord();
        record.setCustId(9L);
        record.setFirstName("Immanuel");
        record.setMiddleName("Madeline");
        record.setLastName("Kessler");
        record.setSsn(20973888L);
        record.setDobYyyyMmDd("1961-06-08");
        record.setFicoCreditScore(700);
        record.setPhoneNum1("(908)119-8310");
        record.setPhoneNum2("(704)693-8684");
        return record;
    }

    @Test
    void readsXrefThenAccountThenCustomerAndFormatsTheScreen() {
        when(cardXrefRepository.findByAcctId(1L)).thenReturn(Optional.of(xref()));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account()));
        when(customerRepository.findById(9L)).thenReturn(Optional.of(customer()));

        AccountViewResponse response = service().view("00000000001");

        InOrder order = inOrder(cardXrefRepository, accountRepository, customerRepository);
        order.verify(cardXrefRepository).findByAcctId(1L);
        order.verify(accountRepository).findById(1L);
        order.verify(customerRepository).findById(9L);

        assertThat(response.accountId).isEqualTo("00000000001");
        assertThat(response.custId).isEqualTo("000000009");
        assertThat(response.ssn).isEqualTo("020-97-3888");
        // PICOUT='+ZZZ,ZZZ,ZZZ.99': the sign is the fixed leftmost character, the digits are
        // zero suppressed behind it (COACTVW.bms:159-163).
        assertThat(response.currBal).isEqualTo("-        197.13");
        assertThat(response.creditLimit).isEqualTo("+      5,000.00");
        assertThat(response.ficoScore).isEqualTo("700");
        assertThat(response.infoMessage).isEqualTo("Displaying details of given Account");
    }

    @Test
    void aMissingXrefRowStopsBeforeTheAccountRead() {
        when(cardXrefRepository.findByAcctId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().view("00000000001"))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account:00000000001 not found in Cross ref file.  Resp:000000013  Reas:0000");
        verify(accountRepository, never()).findById(anyLong());
    }

    @Test
    void aMissingAccountRowStopsBeforeTheCustomerRead() {
        when(cardXrefRepository.findByAcctId(1L)).thenReturn(Optional.of(xref()));
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().view("00000000001"))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account:00000000001 not found in Acct Master file.Resp:000000013  Reas:0000");
        verify(customerRepository, never()).findById(anyLong());
    }

    @Test
    void aMissingCustomerRowReportsTheCustomerId() {
        when(cardXrefRepository.findByAcctId(1L)).thenReturn(Optional.of(xref()));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account()));
        when(customerRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().view("00000000001"))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("CustId:000000009 not found in customer master.Resp: 000000013  REAS:0000000");
    }

    @Test
    void theFilterIsEditedBeforeAnyRead() {
        assertThatThrownBy(() -> service().view("   "))
                .isInstanceOf(AccountFilterException.class)
                .hasMessage("No input received");
        assertThatThrownBy(() -> service().view("0000000000X"))
                .isInstanceOf(AccountFilterException.class)
                .hasMessage("Account Filter must  be a non-zero 11 digit number");
        assertThatThrownBy(() -> service().view("00000000000"))
                .isInstanceOf(AccountFilterException.class)
                .hasMessage("Account Filter must  be a non-zero 11 digit number");
        verify(cardXrefRepository, never()).findByAcctId(anyLong());
    }
}
