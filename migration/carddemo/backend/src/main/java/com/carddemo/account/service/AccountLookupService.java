package com.carddemo.account.service;

import com.carddemo.account.exception.AccountNotFoundException;
import com.carddemo.account.util.AccountFormat;
import com.carddemo.account.util.AccountMessages;
import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.CustomerRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.CardXrefRepository;
import com.carddemo.common.repository.CustomerRepository;
import org.springframework.stereotype.Service;

/**
 * 9200-GETCARDXREF-BYACCT, 9300-GETACCTDATA-BYACCT and 9400-GETCUSTDATA-BYCUST
 * (COACTVWC:687-870; the same three reads are 9500-9700 of COACTUPC). The order matters: the
 * first read that fails owns the message and the flow stops there.
 */
@Service
public class AccountLookupService {

    /** DFHRESP(NOTFND); the migrated reads have no RESP2, so the reason code is always zero. */
    private static final int RESP_NOTFND = 13;
    private static final int REASON_NONE = 0;

    private final CardXrefRepository cardXrefRepository;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    public AccountLookupService(CardXrefRepository cardXrefRepository,
                                AccountRepository accountRepository,
                                CustomerRepository customerRepository) {
        this.cardXrefRepository = cardXrefRepository;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    public AccountCustomerPair lookup(long accountId) {
        String accountId11 = AccountFormat.accountId(accountId);
        CardXrefRecord xref = cardXrefRepository.findByAcctId(accountId)
                .orElseThrow(() -> new AccountNotFoundException(
                        AccountMessages.xrefNotFound(accountId11, RESP_NOTFND, REASON_NONE)));
        AccountRecord account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(
                        AccountMessages.accountNotFound(accountId11, RESP_NOTFND, REASON_NONE)));
        CustomerRecord customer = customerRepository.findById(xref.getCustId())
                .orElseThrow(() -> new AccountNotFoundException(AccountMessages.customerNotFound(
                        AccountFormat.customerId(xref.getCustId()), RESP_NOTFND, REASON_NONE)));
        return new AccountCustomerPair(account, customer);
    }
}
