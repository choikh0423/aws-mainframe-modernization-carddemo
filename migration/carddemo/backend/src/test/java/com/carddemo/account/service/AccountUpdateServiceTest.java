package com.carddemo.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carddemo.account.AccountFieldsFixture;
import com.carddemo.account.dto.AccountFields;
import com.carddemo.account.dto.AccountUpdateRequest;
import com.carddemo.account.dto.AccountUpdateResponse;
import com.carddemo.account.dto.AccountUpdateState;
import com.carddemo.account.exception.AccountUpdateFailedException;
import com.carddemo.account.exception.AccountValidationException;
import com.carddemo.account.exception.RecordLockException;
import com.carddemo.account.exception.StaleRecordException;
import com.carddemo.account.validator.AccountChangeDetector;
import com.carddemo.account.validator.AccountDateValidator;
import com.carddemo.account.validator.AccountFilterValidator;
import com.carddemo.account.validator.AccountUpdateValidator;
import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CustomerRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.CustomerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PessimisticLockException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * CAUP turn handling with the files mocked out: the states (FR-AU-06, FR-AU-08, FR-AU-09),
 * the second edit pass on F5 (FR-AU-09), the lock failures (FR-AU-32, FR-AU-33), the stale
 * record check (FR-AU-31) and the rewrite failure (FR-AU-34).
 */
@ExtendWith(MockitoExtension.class)
class AccountUpdateServiceTest {

    private static final long ACCOUNT_ID = 1L;
    private static final long CUSTOMER_ID = 1L;

    @Mock
    private AccountLookupService lookupService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private EntityManager entityManager;

    private AccountUpdateService service;
    private AccountFields original;

    @BeforeEach
    void setUp() {
        AccountUpdateValidator updateValidator = new AccountUpdateValidator(
                new AccountDateValidator(),
                Clock.fixed(LocalDate.of(2024, 5, 20).atStartOfDay(ZoneOffset.UTC).toInstant(),
                        ZoneOffset.UTC));
        service = new AccountUpdateService(new AccountFilterValidator(), lookupService, updateValidator,
                new AccountChangeDetector(), accountRepository, customerRepository);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        original = AccountFieldsFixture.validScreen();
    }

    private AccountRecord storedAccount() {
        AccountRecord record = new AccountRecord();
        record.setAcctId(ACCOUNT_ID);
        record.setActiveStatus("Y");
        record.setCurrBal(new BigDecimal("194.00"));
        record.setCreditLimit(new BigDecimal("2020.00"));
        record.setCashCreditLimit(new BigDecimal("1020.00"));
        record.setCurrCycCredit(new BigDecimal("0.00"));
        record.setCurrCycDebit(new BigDecimal("0.00"));
        record.setOpenDate("2014-11-20");
        record.setExpiraionDate("2025-05-20");
        record.setReissueDate("2025-05-20");
        record.setGroupId("");
        return record;
    }

    private CustomerRecord storedCustomer() {
        CustomerRecord record = new CustomerRecord();
        record.setCustId(CUSTOMER_ID);
        record.setFirstName("Immanuel");
        record.setMiddleName("Madeline");
        record.setLastName("Kessler");
        record.setAddrLine1("618 Deshaun Route");
        record.setAddrLine2("Apt. 802");
        record.setAddrLine3("Altenwerthshire");
        record.setAddrStateCd("NC");
        record.setAddrZip("27546");
        record.setAddrCountryCd("USA");
        record.setPhoneNum1("(908)119-8310");
        record.setPhoneNum2("(704)693-8684");
        record.setSsn(20973888L);
        record.setGovtIssuedId("00000000000049368437");
        record.setDobYyyyMmDd("1961-06-08");
        record.setEftAccountId("0053581756");
        record.setPriCardHolderInd("Y");
        record.setFicoCreditScore(700);
        return record;
    }

    private AccountUpdateRequest request(AccountFields updated) {
        AccountUpdateRequest request = new AccountUpdateRequest();
        request.original = original;
        request.updated = updated;
        return request;
    }

    private AccountFields changed() {
        AccountFields updated = AccountFieldsFixture.copyOf(original);
        updated.creditLimit = "+      3,000.00";
        return updated;
    }

    private void lockableRecords() {
        when(entityManager.find(AccountRecord.class, ACCOUNT_ID, LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(storedAccount());
        when(entityManager.find(CustomerRecord.class, CUSTOMER_ID, LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(storedCustomer());
    }

    @Test
    void fetchReturnsTheDetailsState() {
        when(lookupService.lookup(ACCOUNT_ID))
                .thenReturn(new AccountCustomerPair(storedAccount(), storedCustomer()));

        AccountUpdateResponse response = service.fetch("00000000001");

        assertThat(response.state).isEqualTo(AccountUpdateState.SHOW_DETAILS);
        assertThat(response.infoMessage).isEqualTo("Update account details presented above.");
        assertThat(response.details.accountId).isEqualTo("00000000001");
    }

    @Test
    void validateReportsNoChangeBeforeRunningTheEdits() {
        // The stored row itself would fail the FICO edit, and the screen was not touched.
        original.ficoScore = "274";
        AccountFields untouched = AccountFieldsFixture.copyOf(original);

        AccountUpdateResponse response = service.validate("00000000001", request(untouched));

        // The FICO edit would have failed, but the no-change test comes first.
        assertThat(response.state).isEqualTo(AccountUpdateState.SHOW_DETAILS);
        assertThat(response.errorMessage)
                .isEqualTo("No change detected with respect to values fetched.");
    }

    @Test
    void validateReturnsTheConfirmationStateForACleanChange() {
        AccountUpdateResponse response = service.validate("00000000001", request(changed()));

        assertThat(response.state).isEqualTo(AccountUpdateState.CHANGES_OK_NOT_CONFIRMED);
        assertThat(response.infoMessage).isEqualTo("Changes validated.Press F5 to save");
        assertThat(response.errorMessage).isNull();
    }

    @Test
    void validateKeepsTheEnteredValuesWhenAnEditFails() {
        AccountFields updated = changed();
        updated.city = "Chicago1";

        AccountUpdateResponse response = service.validate("00000000001", request(updated));

        assertThat(response.state).isEqualTo(AccountUpdateState.CHANGES_NOT_OK);
        assertThat(response.errorMessage).isEqualTo("City can have alphabets only.");
        assertThat(response.details.city).isEqualTo("Chicago1");
    }

    @Test
    void saveRewritesBothRecordsInOrder() {
        lockableRecords();

        AccountUpdateResponse response = service.save("00000000001", request(changed()));

        assertThat(response.state).isEqualTo(AccountUpdateState.CHANGES_OKAYED_AND_DONE);
        assertThat(response.infoMessage).isEqualTo("Changes committed to database");
        verify(accountRepository).save(any(AccountRecord.class));
        verify(customerRepository).save(any(CustomerRecord.class));
    }

    @Test
    void saveRunsTheEditsAgainBeforeTouchingTheFiles() {
        AccountFields updated = changed();
        updated.state = "XX";

        assertThatThrownBy(() -> service.save("00000000001", request(updated)))
                .isInstanceOf(AccountValidationException.class)
                .hasMessage("State: is not a valid state code");
        verify(accountRepository, never()).save(any(AccountRecord.class));
    }

    @Test
    void saveRefusesAnUnchangedScreen() {
        assertThatThrownBy(() ->
                service.save("00000000001", request(AccountFieldsFixture.copyOf(original))))
                .isInstanceOf(AccountValidationException.class)
                .hasMessage("No change detected with respect to values fetched.");
    }

    @Test
    void anUnlockableAccountRecordStopsTheSave() {
        when(entityManager.find(AccountRecord.class, ACCOUNT_ID, LockModeType.PESSIMISTIC_WRITE))
                .thenThrow(new PessimisticLockException("busy"));

        assertThatThrownBy(() -> service.save("00000000001", request(changed())))
                .isInstanceOf(RecordLockException.class)
                .hasMessage("Could not lock account record for update");
    }

    @Test
    void anUnlockableCustomerRecordStopsTheSave() {
        when(entityManager.find(AccountRecord.class, ACCOUNT_ID, LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(storedAccount());
        when(entityManager.find(CustomerRecord.class, CUSTOMER_ID, LockModeType.PESSIMISTIC_WRITE))
                .thenThrow(new PessimisticLockException("busy"));

        assertThatThrownBy(() -> service.save("00000000001", request(changed())))
                .isInstanceOf(RecordLockException.class)
                .hasMessage("Could not lock customer record for update");
    }

    @Test
    void aRecordChangedSinceTheFetchIsNotOverwritten() {
        AccountRecord movedOn = storedAccount();
        movedOn.setCashCreditLimit(new BigDecimal("9999.00"));
        when(entityManager.find(AccountRecord.class, ACCOUNT_ID, LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(movedOn);
        when(entityManager.find(CustomerRecord.class, CUSTOMER_ID, LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(storedCustomer());

        assertThatThrownBy(() -> service.save("00000000001", request(changed())))
                .isInstanceOf(StaleRecordException.class)
                .hasMessage("Record changed by some one else. Please review");
        verify(accountRepository, never()).save(any(AccountRecord.class));
    }

    @Test
    void aFailedRewriteReportsTheUpdateFailure() {
        lockableRecords();
        doThrow(new org.springframework.dao.DataIntegrityViolationException("rewrite"))
                .when(accountRepository).save(any(AccountRecord.class));

        assertThatThrownBy(() -> service.save("00000000001", request(changed())))
                .isInstanceOf(AccountUpdateFailedException.class)
                .hasMessage("Update of record failed");
        verify(customerRepository, never()).save(any(CustomerRecord.class));
    }

    @Test
    void theFilterIsEditedOnEveryTurn() {
        assertThatThrownBy(() -> service.validate("1234", request(changed())))
                .hasMessage("Account Number if supplied must be a 11 digit Non-Zero Number");
        assertThatThrownBy(() -> service.save("1234", request(changed())))
                .hasMessage("Account Number if supplied must be a 11 digit Non-Zero Number");
        verify(lookupService, never()).lookup(eq(1234L));
    }
}
