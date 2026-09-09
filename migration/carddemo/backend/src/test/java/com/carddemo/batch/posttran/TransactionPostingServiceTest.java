package com.carddemo.batch.posttran;

import com.carddemo.common.batch.AbendData;
import com.carddemo.common.batch.AbendException;
import com.carddemo.common.batch.AbendService;
import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.DailyTransactionRecord;
import com.carddemo.common.domain.TransactionCategoryBalanceId;
import com.carddemo.common.domain.TransactionCategoryBalanceRecord;
import com.carddemo.common.domain.TransactionRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.TransactionCategoryBalanceRepository;
import com.carddemo.common.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 2000-POST-TRANSACTION (CBTRN02C.cbl:424-579): FR-S11-010 … FR-S11-016, FR-S11-022. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionPostingServiceTest {

    private static final long ACCT_ID = 11L;
    private static final TransactionCategoryBalanceId CATEGORY_KEY =
            new TransactionCategoryBalanceId(ACCT_ID, "01", 5001);

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionCategoryBalanceRepository categoryBalanceRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AbendService abendService;

    private AccountRecord account;
    private TransactionPostingService postingService;

    @BeforeEach
    void setUp() {
        account = account("1000.00", "100.00", "-20.00");
        postingService = new TransactionPostingService(transactionRepository, categoryBalanceRepository,
                accountRepository, new Db2TimestampFormatter(
                Clock.fixed(Instant.parse("2024-06-01T10:11:12.34Z"), ZoneId.of("UTC"))), abendService);
        when(categoryBalanceRepository.findById(CATEGORY_KEY)).thenReturn(Optional.empty());
    }

    @Test
    void copiesEveryDailyTransactionFieldOntoThePostedTransaction() {
        DailyTransactionRecord daily = daily("13.75");
        daily.setProcTs("1999-01-01-00.00.00.000000");

        postingService.post(daily, validation());

        TransactionRecord posted = capturePosted();
        assertThat(posted.getId()).isEqualTo(daily.getId());
        assertThat(posted.getTypeCd()).isEqualTo("01");
        assertThat(posted.getCatCd()).isEqualTo(5001);
        assertThat(posted.getSource()).isEqualTo("POS TERM");
        assertThat(posted.getDescription()).isEqualTo("Groceries");
        assertThat(posted.getAmount()).isEqualByComparingTo("13.75");
        assertThat(posted.getMerchantId()).isEqualTo(123456789L);
        assertThat(posted.getMerchantName()).isEqualTo("MERCHANT");
        assertThat(posted.getMerchantCity()).isEqualTo("CITY");
        assertThat(posted.getMerchantZip()).isEqualTo("12345");
        assertThat(posted.getCardNum()).isEqualTo("4111111111111111");
        assertThat(posted.getOrigTs()).isEqualTo("2024-03-07-09.04.05.120000");
        assertThat(posted.getProcTs()).isEqualTo("2024-06-01-10.11.12.340000");
    }

    @Test
    void createsCategoryBalanceWhenAbsent() {
        postingService.post(daily("13.75"), validation());

        TransactionCategoryBalanceRecord balance = captureBalance();
        assertThat(balance.getId()).isEqualTo(CATEGORY_KEY);
        assertThat(balance.getBal()).isEqualByComparingTo("13.75");
    }

    @Test
    void addsToAnExistingCategoryBalance() {
        TransactionCategoryBalanceRecord existing = new TransactionCategoryBalanceRecord();
        existing.setId(CATEGORY_KEY);
        existing.setBal(new BigDecimal("100.00"));
        when(categoryBalanceRepository.findById(CATEGORY_KEY)).thenReturn(Optional.of(existing));

        postingService.post(daily("13.75"), validation());

        assertThat(captureBalance().getBal()).isEqualByComparingTo("113.75");
    }

    @Test
    void addsAmountToAccountCurrentBalance() {
        postingService.post(daily("-40.25"), validation());

        assertThat(captureAccount().getCurrBal()).isEqualByComparingTo("959.75");
    }

    @Test
    void addsNonNegativeAmountToCurrentCycleCredit() {
        postingService.post(daily("13.75"), validation());

        AccountRecord updated = captureAccount();
        assertThat(updated.getCurrCycCredit()).isEqualByComparingTo("113.75");
        assertThat(updated.getCurrCycDebit()).isEqualByComparingTo("-20.00");
    }

    @Test
    void addsAZeroAmountToCurrentCycleCredit() {
        postingService.post(daily("0.00"), validation());

        AccountRecord updated = captureAccount();
        assertThat(updated.getCurrCycCredit()).isEqualByComparingTo("100.00");
        assertThat(updated.getCurrCycDebit()).isEqualByComparingTo("-20.00");
    }

    @Test
    void addsNegativeAmountToCurrentCycleDebit() {
        postingService.post(daily("-40.25"), validation());

        AccountRecord updated = captureAccount();
        assertThat(updated.getCurrCycDebit()).isEqualByComparingTo("-60.25");
        assertThat(updated.getCurrCycCredit()).isEqualByComparingTo("100.00");
    }

    @Test
    void abendsOnDuplicateTransactionId() {
        DailyTransactionRecord daily = daily("13.75");
        when(transactionRepository.existsById(daily.getId())).thenReturn(true);
        when(abendService.abend(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new AbendException(new AbendData("0999", "CBTRN02C",
                        "ERROR WRITING TO TRANSACTION FILE", "FILE STATUS IS: NNNN0022")));

        assertThatThrownBy(() -> postingService.post(daily, validation()))
                .isInstanceOf(AbendException.class);

        verify(abendService).abend("0999", "CBTRN02C", "ERROR WRITING TO TRANSACTION FILE",
                "FILE STATUS IS: NNNN0022");
        verify(transactionRepository, never()).save(any());
    }

    private TransactionRecord capturePosted() {
        ArgumentCaptor<TransactionRecord> captor = ArgumentCaptor.forClass(TransactionRecord.class);
        verify(transactionRepository).save(captor.capture());
        return captor.getValue();
    }

    private TransactionCategoryBalanceRecord captureBalance() {
        ArgumentCaptor<TransactionCategoryBalanceRecord> captor =
                ArgumentCaptor.forClass(TransactionCategoryBalanceRecord.class);
        verify(categoryBalanceRepository).save(captor.capture());
        return captor.getValue();
    }

    private AccountRecord captureAccount() {
        ArgumentCaptor<AccountRecord> captor = ArgumentCaptor.forClass(AccountRecord.class);
        verify(accountRepository).save(captor.capture());
        return captor.getValue();
    }

    private ValidationResult validation() {
        CardXrefRecord xref = new CardXrefRecord();
        xref.setCardNum("4111111111111111");
        xref.setCustId(1L);
        xref.setAcctId(ACCT_ID);
        return ValidationResult.accepted(xref, account);
    }

    private static AccountRecord account(String currBal, String cycCredit, String cycDebit) {
        AccountRecord account = new AccountRecord();
        account.setAcctId(ACCT_ID);
        account.setActiveStatus("Y");
        account.setCurrBal(new BigDecimal(currBal));
        account.setCreditLimit(new BigDecimal("5000.00"));
        account.setCurrCycCredit(new BigDecimal(cycCredit));
        account.setCurrCycDebit(new BigDecimal(cycDebit));
        account.setExpiraionDate("2099-12-31");
        return account;
    }

    private static DailyTransactionRecord daily(String amount) {
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
        daily.setCardNum("4111111111111111");
        daily.setOrigTs("2024-03-07-09.04.05.120000");
        daily.setProcTs("");
        return daily;
    }
}
