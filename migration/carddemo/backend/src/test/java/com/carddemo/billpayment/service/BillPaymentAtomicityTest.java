package com.carddemo.billpayment.service;

import com.carddemo.billpayment.dto.BillPaymentRequest;
import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * FR-BP-40 — the payment is one unit of work. Under CICS the TRANSACT WRITE and
 * the ACCTDAT REWRITE were covered by the same implicit syncpoint
 * (app/cbl/COBIL00C.cbl:233-235); here the failing REWRITE must roll the written
 * transaction back rather than leave a payment with an unchanged balance.
 *
 * <p>The test runs without {@code @Transactional} so the service's own
 * transaction really commits or rolls back.
 */
@SpringBootTest
class BillPaymentAtomicityTest {

    @MockBean
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BillPaymentService service;

    @Test
    void frBp40_failedAccountUpdateRollsBackTheTransactionWrite() {
        AccountRecord account = new AccountRecord();
        account.setAcctId(1L);
        account.setCurrBal(new BigDecimal("194.00"));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(AccountRecord.class)))
                .thenThrow(new IllegalStateException("Unable to Update Account..."));

        BillPaymentRequest request = new BillPaymentRequest();
        request.setAccountId("1");
        request.setConfirm("Y");

        assertThatThrownBy(() -> service.enter(request)).isInstanceOf(IllegalStateException.class);

        assertThat(transactionRepository.findById("0000000000000031")).isEmpty();
    }
}
