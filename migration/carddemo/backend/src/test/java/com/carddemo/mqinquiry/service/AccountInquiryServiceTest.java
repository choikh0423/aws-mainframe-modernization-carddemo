package com.carddemo.mqinquiry.service;

import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.mqinquiry.message.InquiryRequestMessage;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.QueryTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;

/** CDRA business rules: FR-MQI-010..016 / FR-COACCT01-01..09. */
class AccountInquiryServiceTest {

    private static final String REQUEST_QUEUE = "CARDDEMO.REQUEST.QUEUE";

    private AccountRepository accounts;
    private AccountInquiryService service;

    @BeforeEach
    void setUp() {
        accounts = Mockito.mock(AccountRepository.class);
        service = new AccountInquiryService(accounts);
        Mockito.when(accounts.findById(anyLong())).thenReturn(Optional.empty());
        Mockito.when(accounts.findById(1L)).thenReturn(Optional.of(account()));
    }

    @Test
    void repliesWithTheLabelledAccountBlockWhenTheAccountExists() {
        String reply = inquire("INQA", 1L);

        assertThat(reply).hasSize(1000);
        assertThat(reply.substring(0, 13)).isEqualTo("ACCOUNT ID : ");
        assertThat(reply.substring(13, 24)).isEqualTo("00000000001");
        assertThat(reply.substring(24, 41)).isEqualTo("ACCOUNT STATUS : ");
        assertThat(reply.substring(41, 42)).isEqualTo("Y");
        assertThat(reply.substring(42, 52)).isEqualTo("BALANCE : ");
        assertThat(reply.substring(52, 64)).isEqualTo("00000001940{");
        assertThat(reply.substring(64, 79)).isEqualTo("CREDIT LIMIT : ");
        assertThat(reply.substring(79, 91)).isEqualTo("00000020200{");
        assertThat(reply.substring(91, 104)).isEqualTo("CASH LIMIT : ");
        assertThat(reply.substring(104, 116)).isEqualTo("00000010200{");
        assertThat(reply.substring(116, 128)).isEqualTo("OPEN DATE : ");
        assertThat(reply.substring(128, 138)).isEqualTo("2014-11-20");
        assertThat(reply.substring(138, 150)).isEqualTo("EXPR DATE : ");
        assertThat(reply.substring(150, 160)).isEqualTo("2025-05-20");
        assertThat(reply.substring(160, 172)).isEqualTo("REIS DATE : ");
        assertThat(reply.substring(172, 182)).isEqualTo("2025-05-20");
        assertThat(reply.substring(182, 195)).isEqualTo("CREDIT BAL : ");
        assertThat(reply.substring(195, 207)).isEqualTo("00000000000{");
        assertThat(reply.substring(207, 219)).isEqualTo("DEBIT BAL : ");
        assertThat(reply.substring(219, 231)).isEqualTo("00000000250}");
        assertThat(reply.substring(231, 242)).isEqualTo("GROUP ID : ");
        assertThat(reply.substring(242, 252)).isEqualTo("GRP0000001");
        assertThat(reply.substring(252)).isBlank();
    }

    /** FR-MQI-016: the zip is part of the record but never part of the reply. */
    @Test
    void neverReturnsTheAddressZip() {
        assertThat(inquire("INQA", 1L)).doesNotContain("A000000000");
    }

    @Test
    void repliesInvalidRequestWithoutTheFunctionLabelWhenTheAccountIsNotFound() {
        assertThat(inquire("INQA", 99L).stripTrailing())
                .isEqualTo("INVALID REQUEST PARAMETERS ACCT ID : 00000000099");
    }

    @Test
    void repliesInvalidRequestWithTheRawFunctionWhenTheFunctionIsNotInqa() {
        assertThat(inquire("INQB", 1L).stripTrailing())
                .isEqualTo("INVALID REQUEST PARAMETERS ACCT ID : 00000000001FUNCTION : INQB");
        assertThat(inquire("inqa", 1L).stripTrailing())
                .isEqualTo("INVALID REQUEST PARAMETERS ACCT ID : 00000000001FUNCTION : inqa");
        Mockito.verify(accounts, Mockito.never()).findById(anyLong());
    }

    @Test
    void repliesInvalidRequestWhenTheKeyIsNotGreaterThanZero() {
        assertThat(inquire("INQA", 0L).stripTrailing())
                .isEqualTo("INVALID REQUEST PARAMETERS ACCT ID : 00000000000FUNCTION : INQA");
        assertThat(service.inquire(InquiryRequestMessage.parse("INQANOTANUMBER "), REQUEST_QUEUE)
                .payload().stripTrailing())
                .isEqualTo("INVALID REQUEST PARAMETERS ACCT ID : 00000000000FUNCTION : INQA");
    }

    /** FR-MQI-015: a read failure reports on the error queue and does not reply. */
    @Test
    void reportsAReadFailureOnTheErrorQueueInsteadOfReplying() {
        Mockito.when(accounts.findById(2L)).thenThrow(new QueryTimeoutException("ACCTDAT"));

        MqInquiryResult result = service.inquire(InquiryRequestMessage.parse(
                InquiryRequestMessage.render("INQA", 2L)), REQUEST_QUEUE);

        assertThat(result.errorReport()).isTrue();
        assertThat(result.payload()).hasSize(1000);
        assertThat(result.payload().substring(27, 52)).isEqualTo("ERROR WHILE READING ACCTF");
        assertThat(result.payload().substring(65, 113).stripTrailing()).isEqualTo(REQUEST_QUEUE);
    }

    private String inquire(String func, long key) {
        MqInquiryResult result = service.inquire(
                InquiryRequestMessage.parse(InquiryRequestMessage.render(func, key)), REQUEST_QUEUE);
        assertThat(result.errorReport()).isFalse();
        return result.payload();
    }

    private static AccountRecord account() {
        AccountRecord account = new AccountRecord();
        account.setAcctId(1L);
        account.setActiveStatus("Y");
        account.setCurrBal(new BigDecimal("194.00"));
        account.setCreditLimit(new BigDecimal("2020.00"));
        account.setCashCreditLimit(new BigDecimal("1020.00"));
        account.setOpenDate("2014-11-20");
        account.setExpiraionDate("2025-05-20");
        account.setReissueDate("2025-05-20");
        account.setCurrCycCredit(new BigDecimal("0.00"));
        account.setCurrCycDebit(new BigDecimal("-25.00"));
        account.setAddrZip("A000000000");
        account.setGroupId("GRP0000001");
        return account;
    }
}
