package com.carddemo.batch.posttran;

import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.DailyTransactionRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.CardXrefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 1500-VALIDATE-TRAN (CBTRN02C.cbl:370-422): FR-S11-004 … FR-S11-009, FR-S11-026. */
@ExtendWith(MockitoExtension.class)
class TransactionValidationServiceTest {

    private static final String CARD = "4111111111111111";
    private static final long ACCT_ID = 11L;

    @Mock
    private CardXrefRepository cardXrefRepository;
    @Mock
    private AccountRepository accountRepository;

    private TransactionValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new TransactionValidationService(cardXrefRepository, accountRepository);
    }

    @Test
    void acceptsATransactionInsideTheLimitOnALiveAccount() {
        givenXref();
        givenAccount(account("5000.00", "1000.00", "200.00", "2099-12-31"));

        ValidationResult result = validationService.validate(daily("100.00", "2024-03-07-09.04.05.120000"));

        assertThat(result.isRejected()).isFalse();
        assertThat(result.reason()).isZero();
        assertThat(result.description()).isEmpty();
        assertThat(result.account().getAcctId()).isEqualTo(ACCT_ID);
        assertThat(result.xref().getAcctId()).isEqualTo(ACCT_ID);
    }

    @Test
    void rejectsUnknownCardWith100() {
        when(cardXrefRepository.findById(CARD)).thenReturn(Optional.empty());

        ValidationResult result = validationService.validate(daily("100.00", "2024-03-07-09.04.05.120000"));

        assertThat(result.reason()).isEqualTo(100);
        assertThat(result.description()).isEqualTo("INVALID CARD NUMBER FOUND");
    }

    @Test
    void doesNotLookUpAccountWhenCardIsUnknown() {
        when(cardXrefRepository.findById(CARD)).thenReturn(Optional.empty());

        validationService.validate(daily("100.00", "2024-03-07-09.04.05.120000"));

        verify(accountRepository, never()).findById(any());
    }

    @Test
    void rejectsMissingAccountWith101() {
        givenXref();
        when(accountRepository.findById(ACCT_ID)).thenReturn(Optional.empty());

        ValidationResult result = validationService.validate(daily("100.00", "2024-03-07-09.04.05.120000"));

        assertThat(result.reason()).isEqualTo(101);
        assertThat(result.description()).isEqualTo("ACCOUNT RECORD NOT FOUND");
        assertThat(result.account()).isNull();
    }

    @Test
    void rejectsOverlimitWith102() {
        givenXref();
        // 900.00 - 100.00 + 250.00 = 1050.00 > 1000.00
        givenAccount(account("5000.00", "1000.00", "900.00", "2099-12-31", "100.00"));

        ValidationResult result = validationService.validate(daily("250.00", "2024-03-07-09.04.05.120000"));

        assertThat(result.reason()).isEqualTo(102);
        assertThat(result.description()).isEqualTo("OVERLIMIT TRANSACTION");
    }

    @Test
    void acceptsTransactionExactlyOnTheCreditLimit() {
        givenXref();
        // 900.00 - 100.00 + 200.00 = 1000.00, and ACCT-CREDIT-LIMIT >= WS-TEMP-BAL continues
        givenAccount(account("5000.00", "1000.00", "900.00", "2099-12-31", "100.00"));

        ValidationResult result = validationService.validate(daily("200.00", "2024-03-07-09.04.05.120000"));

        assertThat(result.isRejected()).isFalse();
    }

    @Test
    void rejectsExpiredAccountWith103() {
        givenXref();
        givenAccount(account("5000.00", "1000.00", "0.00", "2024-03-06"));

        ValidationResult result = validationService.validate(daily("10.00", "2024-03-07-09.04.05.120000"));

        assertThat(result.reason()).isEqualTo(103);
        assertThat(result.description()).isEqualTo("TRANSACTION RECEIVED AFTER ACCT EXPIRATION");
    }

    @Test
    void acceptsTransactionOnTheExpirationDate() {
        givenXref();
        givenAccount(account("5000.00", "1000.00", "0.00", "2024-03-07"));

        ValidationResult result = validationService.validate(daily("10.00", "2024-03-07-09.04.05.120000"));

        assertThat(result.isRejected()).isFalse();
    }

    @Test
    void expirationOverwritesOverlimitReason() {
        givenXref();
        givenAccount(account("5000.00", "10.00", "0.00", "2024-03-06"));

        ValidationResult result = validationService.validate(daily("500.00", "2024-03-07-09.04.05.120000"));

        assertThat(result.reason()).isEqualTo(103);
        assertThat(result.description()).isEqualTo("TRANSACTION RECEIVED AFTER ACCT EXPIRATION");
    }

    @Test
    void performsNoValidationBeyondXrefAccountLimitAndExpiry() {
        givenXref();
        AccountRecord closed = account("5000.00", "1000.00", "0.00", "2099-12-31");
        closed.setActiveStatus("N");
        givenAccount(closed);

        DailyTransactionRecord unknownTypeAndCategory = daily("10.00", "2024-03-07-09.04.05.120000");
        unknownTypeAndCategory.setTypeCd("ZZ");
        unknownTypeAndCategory.setCatCd(9999);
        unknownTypeAndCategory.setMerchantName("");

        assertThat(validationService.validate(unknownTypeAndCategory).isRejected()).isFalse();
    }

    private void givenXref() {
        CardXrefRecord xref = new CardXrefRecord();
        xref.setCardNum(CARD);
        xref.setCustId(1L);
        xref.setAcctId(ACCT_ID);
        lenient().when(cardXrefRepository.findById(CARD)).thenReturn(Optional.of(xref));
    }

    private void givenAccount(AccountRecord account) {
        when(accountRepository.findById(ACCT_ID)).thenReturn(Optional.of(account));
    }

    private static AccountRecord account(String currBal, String creditLimit, String cycCredit,
                                         String expiration) {
        return account(currBal, creditLimit, cycCredit, expiration, "0.00");
    }

    private static AccountRecord account(String currBal, String creditLimit, String cycCredit,
                                         String expiration, String cycDebit) {
        AccountRecord account = new AccountRecord();
        account.setAcctId(ACCT_ID);
        account.setActiveStatus("Y");
        account.setCurrBal(new BigDecimal(currBal));
        account.setCreditLimit(new BigDecimal(creditLimit));
        account.setCurrCycCredit(new BigDecimal(cycCredit));
        account.setCurrCycDebit(new BigDecimal(cycDebit));
        account.setExpiraionDate(expiration);
        return account;
    }

    private static DailyTransactionRecord daily(String amount, String origTs) {
        DailyTransactionRecord daily = new DailyTransactionRecord();
        daily.setId("0000000000000001");
        daily.setTypeCd("01");
        daily.setCatCd(5001);
        daily.setSource("POS TERM");
        daily.setDescription("Groceries");
        daily.setAmount(new BigDecimal(amount));
        daily.setMerchantId(123456789L);
        daily.setMerchantName("MERCHANT");
        daily.setMerchantCity("CITY");
        daily.setMerchantZip("12345");
        daily.setCardNum(CARD);
        daily.setOrigTs(origTs);
        daily.setProcTs("");
        return daily;
    }
}
