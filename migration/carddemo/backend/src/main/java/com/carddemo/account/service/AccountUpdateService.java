package com.carddemo.account.service;

import com.carddemo.account.dto.AccountFields;
import com.carddemo.account.dto.AccountUpdateRequest;
import com.carddemo.account.dto.AccountUpdateResponse;
import com.carddemo.account.dto.AccountUpdateState;
import com.carddemo.account.exception.AccountUpdateFailedException;
import com.carddemo.account.exception.AccountValidationException;
import com.carddemo.account.exception.RecordLockException;
import com.carddemo.account.exception.StaleRecordException;
import com.carddemo.account.util.AccountMessages;
import com.carddemo.account.validator.AccountChangeDetector;
import com.carddemo.account.validator.AccountFieldEdits;
import com.carddemo.account.validator.AccountFilterValidator;
import com.carddemo.account.validator.AccountUpdateValidator;
import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CustomerRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.CustomerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.PessimisticLockException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CAUP: fetch, validate and save.
 *
 * <p>The legacy program is one pseudo-conversational transaction holding
 * {@code ACUP-OLD-DETAILS} / {@code ACUP-NEW-DETAILS} in its private commarea
 * (COACTUPC:380-870). Here the snapshot travels in the request, so every turn is a stateless
 * call: {@link #fetch} is the ENTER that reads the account, {@link #validate} is the ENTER that
 * runs 1200-EDIT-MAP-INPUTS and 2000-DECIDE-ACTION, and {@link #save} is F5
 * (9600-WRITE-PROCESSING). F12 is the client calling {@link #fetch} again.
 */
@Service
public class AccountUpdateService {

    private final AccountFilterValidator filterValidator;
    private final AccountLookupService lookupService;
    private final AccountUpdateValidator updateValidator;
    private final AccountChangeDetector changeDetector;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public AccountUpdateService(AccountFilterValidator filterValidator,
                                AccountLookupService lookupService,
                                AccountUpdateValidator updateValidator,
                                AccountChangeDetector changeDetector,
                                AccountRepository accountRepository,
                                CustomerRepository customerRepository) {
        this.filterValidator = filterValidator;
        this.lookupService = lookupService;
        this.updateValidator = updateValidator;
        this.changeDetector = changeDetector;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    /** ENTER on the search key: 1210-EDIT-ACCOUNT then the three reads (COACTUPC:1433-1449). */
    @Transactional(readOnly = true)
    public AccountUpdateResponse fetch(String accountId) {
        long editedAccountId = filterValidator.validateUpdateFilter(accountId);
        AccountCustomerPair records = lookupService.lookup(editedAccountId);
        AccountFields details = AccountFields.from(records.getAccount(), records.getCustomer());
        return AccountUpdateResponse.of(AccountUpdateState.SHOW_DETAILS,
                AccountMessages.UPDATE_DETAILS_SHOWN, null, details);
    }

    /** ENTER on a filled screen (COACTUPC:1452-1675, 2562-2595). */
    @Transactional(readOnly = true)
    public AccountUpdateResponse validate(String accountId, AccountUpdateRequest request) {
        filterValidator.validateUpdateFilter(accountId);
        if (!changeDetector.hasChanges(request.original, request.updated)) {
            return AccountUpdateResponse.of(AccountUpdateState.SHOW_DETAILS,
                    AccountMessages.UPDATE_DETAILS_SHOWN, AccountMessages.NO_CHANGE_DETECTED,
                    request.updated);
        }
        String error = updateValidator.validate(request.updated);
        if (error != null) {
            return AccountUpdateResponse.of(AccountUpdateState.CHANGES_NOT_OK,
                    AccountMessages.UPDATE_DETAILS_SHOWN, error, request.updated);
        }
        return AccountUpdateResponse.of(AccountUpdateState.CHANGES_OK_NOT_CONFIRMED,
                AccountMessages.UPDATE_CHANGES_VALIDATED, null, request.updated);
    }

    /** F5 (COACTUPC:2596-2606, 3888-4107). */
    @Transactional
    public AccountUpdateResponse save(String accountId, AccountUpdateRequest request) {
        long editedAccountId = filterValidator.validateUpdateFilter(accountId);
        if (!changeDetector.hasChanges(request.original, request.updated)) {
            throw new AccountValidationException(AccountMessages.NO_CHANGE_DETECTED);
        }
        String error = updateValidator.validate(request.updated);
        if (error != null) {
            throw new AccountValidationException(error);
        }

        AccountRecord account = lockAccount(editedAccountId);
        CustomerRecord customer = lockCustomer(request.original.custId);
        assertUnchanged(request.original, account, customer);

        applyAccount(account, request.updated);
        applyCustomer(customer, request.updated);
        try {
            accountRepository.save(account);
            customerRepository.save(customer);
            entityManager.flush();
        } catch (DataAccessException | PersistenceException ex) {
            throw new AccountUpdateFailedException(AccountMessages.UPDATE_OF_RECORD_FAILED);
        }
        return AccountUpdateResponse.of(AccountUpdateState.CHANGES_OKAYED_AND_DONE,
                AccountMessages.UPDATE_CHANGES_COMMITTED, null,
                AccountFields.from(account, customer));
    }

    /** READ ACCTDAT UPDATE (COACTUPC:3892-3916). */
    private AccountRecord lockAccount(long accountId) {
        try {
            AccountRecord account = entityManager.find(AccountRecord.class, accountId,
                    LockModeType.PESSIMISTIC_WRITE);
            if (account == null) {
                throw new RecordLockException(AccountMessages.COULD_NOT_LOCK_ACCOUNT);
            }
            return account;
        } catch (PessimisticLockException | PessimisticLockingFailureException ex) {
            throw new RecordLockException(AccountMessages.COULD_NOT_LOCK_ACCOUNT);
        }
    }

    /** READ CUSTDAT UPDATE (COACTUPC:3918-3944). */
    private CustomerRecord lockCustomer(String customerId) {
        long id;
        try {
            id = Long.parseLong(AccountFieldEdits.field(customerId, 9).trim());
        } catch (NumberFormatException ex) {
            throw new RecordLockException(AccountMessages.COULD_NOT_LOCK_CUSTOMER);
        }
        try {
            CustomerRecord customer = entityManager.find(CustomerRecord.class, id,
                    LockModeType.PESSIMISTIC_WRITE);
            if (customer == null) {
                throw new RecordLockException(AccountMessages.COULD_NOT_LOCK_CUSTOMER);
            }
            return customer;
        } catch (PessimisticLockException | PessimisticLockingFailureException ex) {
            throw new RecordLockException(AccountMessages.COULD_NOT_LOCK_CUSTOMER);
        }
    }

    /** 9700-CHECK-CHANGE-IN-REC (COACTUPC:4109-4202). */
    private void assertUnchanged(AccountFields snapshot, AccountRecord account,
                                 CustomerRecord customer) {
        AccountFields stored = AccountFields.from(account, customer);
        if (changeDetector.hasChanges(snapshot, stored)) {
            throw new StaleRecordException(AccountMessages.RECORD_CHANGED_BY_SOMEONE_ELSE);
        }
    }

    /** Build ACCT-UPDATE-RECORD (COACTUPC:3956-4002). */
    private void applyAccount(AccountRecord account, AccountFields fields) {
        account.setActiveStatus(fields.activeStatus);
        account.setCurrBal(amount(fields.currBal));
        account.setCreditLimit(amount(fields.creditLimit));
        account.setCashCreditLimit(amount(fields.cashCreditLimit));
        account.setCurrCycCredit(amount(fields.currCycCredit));
        account.setCurrCycDebit(amount(fields.currCycDebit));
        account.setOpenDate(date(fields.openYear, fields.openMonth, fields.openDay));
        account.setExpiraionDate(date(fields.expiryYear, fields.expiryMonth, fields.expiryDay));
        account.setReissueDate(date(fields.reissueYear, fields.reissueMonth, fields.reissueDay));
        account.setGroupId(fields.groupId);
        // ACCT-UPDATE-ADDR-ZIP is left as the INITIALIZE set it: the account's own zip is wiped on
        // every save because COACTUPC never moves it into the update record (quirk FR-AQ-08).
        account.setAddrZip("");
    }

    /** Build CUST-UPDATE-RECORD (COACTUPC:4007-4059). */
    private void applyCustomer(CustomerRecord customer, AccountFields fields) {
        customer.setFirstName(fields.firstName);
        customer.setMiddleName(fields.middleName);
        customer.setLastName(fields.lastName);
        customer.setAddrLine1(fields.addrLine1);
        customer.setAddrLine2(fields.addrLine2);
        customer.setAddrLine3(fields.city);
        customer.setAddrStateCd(fields.state);
        customer.setAddrCountryCd(fields.country);
        customer.setAddrZip(fields.zip);
        customer.setPhoneNum1(phone(fields.phone1Area, fields.phone1Prefix, fields.phone1Line));
        customer.setPhoneNum2(phone(fields.phone2Area, fields.phone2Prefix, fields.phone2Line));
        customer.setSsn(Long.parseLong(fields.ssnPart1 + fields.ssnPart2 + fields.ssnPart3));
        customer.setGovtIssuedId(fields.govtIssuedId);
        customer.setDobYyyyMmDd(date(fields.dobYear, fields.dobMonth, fields.dobDay));
        customer.setEftAccountId(fields.eftAccountId);
        customer.setPriCardHolderInd(fields.priCardHolderInd);
        customer.setFicoCreditScore(Integer.parseInt(fields.ficoScore.trim()));
    }

    private BigDecimal amount(String value) {
        BigDecimal parsed = AccountFieldEdits.parseAmount(value);
        return parsed == null ? BigDecimal.ZERO : parsed.setScale(2, RoundingMode.HALF_UP);
    }

    private String date(String year, String month, String day) {
        return year + "-" + month + "-" + day;
    }

    private String phone(String area, String prefix, String line) {
        if (AccountFieldEdits.notSupplied(area) && AccountFieldEdits.notSupplied(prefix)
                && AccountFieldEdits.notSupplied(line)) {
            return "";
        }
        return "(" + area + ")" + prefix + "-" + line;
    }
}
