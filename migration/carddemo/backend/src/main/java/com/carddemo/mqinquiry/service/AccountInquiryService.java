package com.carddemo.mqinquiry.service;

import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.mqinquiry.message.InquiryRequestMessage;
import com.carddemo.mqinquiry.message.MqErrorReport;
import com.carddemo.mqinquiry.message.MqInquiryLayout;

import java.math.BigDecimal;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CDRA / COACCT01: answers an MQ account inquiry from ACCTDAT
 * (app/app-vsam-mq/cbl/COACCT01.cbl:390-457).
 */
@Service
public class AccountInquiryService {

    /** The only function the program serves (COACCT01.cbl:393). */
    public static final String FUNCTION_INQUIRE_ACCOUNT = "INQA";

    private static final Logger LOG = LoggerFactory.getLogger(AccountInquiryService.class);

    private static final String ACCOUNT_LABEL = "ACCOUNT ID : ";
    private static final String STATUS_LABEL = "ACCOUNT STATUS : ";
    private static final String BALANCE_LABEL = "BALANCE : ";
    private static final String CREDIT_LIMIT_LABEL = "CREDIT LIMIT : ";
    private static final String CASH_LIMIT_LABEL = "CASH LIMIT : ";
    private static final String OPEN_DATE_LABEL = "OPEN DATE : ";
    private static final String EXPIRATION_DATE_LABEL = "EXPR DATE : ";
    private static final String REISSUE_DATE_LABEL = "REIS DATE : ";
    private static final String CYCLE_CREDIT_LABEL = "CREDIT BAL : ";
    private static final String CYCLE_DEBIT_LABEL = "DEBIT BAL : ";
    private static final String GROUP_LABEL = "GROUP ID : ";

    private static final String INVALID_REQUEST = "INVALID REQUEST PARAMETERS ";
    private static final String ACCOUNT_ID_LABEL = "ACCT ID : ";
    private static final String FUNCTION_LABEL = "FUNCTION : ";
    private static final String READ_ERROR_MESSAGE = "ERROR WHILE READING ACCTFILE";

    private static final int ACCOUNT_ID_DIGITS = 11;
    private static final int STATUS_LENGTH = 1;
    private static final int DATE_LENGTH = 10;
    private static final int GROUP_ID_LENGTH = 10;
    private static final int AMOUNT_INTEGER_DIGITS = 10;
    private static final int AMOUNT_SCALE = 2;

    private final AccountRepository accounts;

    public AccountInquiryService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /**
     * 4000-PROCESS-REQUEST-REPLY for CDRA.
     *
     * @param requestQueueName the queue the request was triggered from, reported
     *                         in the error message (COACCT01.cbl:441)
     */
    @Transactional(readOnly = true)
    public MqInquiryResult inquire(InquiryRequestMessage request, String requestQueueName) {
        if (!FUNCTION_INQUIRE_ACCOUNT.equals(request.func()) || !request.keyIsPositive()) {
            return MqInquiryResult.reply(invalidRequestReply(request));
        }
        long accountId = request.keyAsNumber();
        Optional<AccountRecord> account;
        try {
            account = accounts.findById(accountId);
        } catch (DataAccessException e) {
            LOG.error("ACCTDAT read failed for account {}", accountId, e);
            return MqInquiryResult.error(new MqErrorReport("", READ_ERROR_MESSAGE, 0, 0, requestQueueName));
        }
        return MqInquiryResult.reply(
                account.map(this::accountReply).orElseGet(() -> notFoundReply(accountId)));
    }

    /** WS-ACCT-RESPONSE (COACCT01.cbl:130-169, :407-426). */
    private String accountReply(AccountRecord account) {
        StringBuilder reply = new StringBuilder()
                .append(ACCOUNT_LABEL)
                .append(MqInquiryLayout.unsigned(account.getAcctId(), ACCOUNT_ID_DIGITS))
                .append(STATUS_LABEL)
                .append(MqInquiryLayout.alphanumeric(account.getActiveStatus(), STATUS_LENGTH))
                .append(BALANCE_LABEL)
                .append(amount(account.getCurrBal()))
                .append(CREDIT_LIMIT_LABEL)
                .append(amount(account.getCreditLimit()))
                .append(CASH_LIMIT_LABEL)
                .append(amount(account.getCashCreditLimit()))
                .append(OPEN_DATE_LABEL)
                .append(MqInquiryLayout.alphanumeric(account.getOpenDate(), DATE_LENGTH))
                .append(EXPIRATION_DATE_LABEL)
                .append(MqInquiryLayout.alphanumeric(account.getExpiraionDate(), DATE_LENGTH))
                .append(REISSUE_DATE_LABEL)
                .append(MqInquiryLayout.alphanumeric(account.getReissueDate(), DATE_LENGTH))
                .append(CYCLE_CREDIT_LABEL)
                .append(amount(account.getCurrCycCredit()))
                .append(CYCLE_DEBIT_LABEL)
                .append(amount(account.getCurrCycDebit()))
                .append(GROUP_LABEL)
                .append(MqInquiryLayout.alphanumeric(account.getGroupId(), GROUP_ID_LENGTH));
        return MqInquiryLayout.message(reply.toString());
    }

    /** DFHRESP(NOTFND) (COACCT01.cbl:428-435). */
    private String notFoundReply(long accountId) {
        return MqInquiryLayout.message(INVALID_REQUEST + ACCOUNT_ID_LABEL
                + MqInquiryLayout.unsigned(accountId, ACCOUNT_ID_DIGITS));
    }

    /**
     * The ELSE branch (COACCT01.cbl:448-456): the STRING has no separator
     * between WS-KEY and the FUNCTION label, and WS-FUNC is copied raw.
     */
    private String invalidRequestReply(InquiryRequestMessage request) {
        return MqInquiryLayout.message(INVALID_REQUEST + ACCOUNT_ID_LABEL
                + MqInquiryLayout.unsigned(request.keyAsNumber(), ACCOUNT_ID_DIGITS)
                + FUNCTION_LABEL + request.func());
    }

    private static String amount(BigDecimal value) {
        return MqInquiryLayout.signed(value, AMOUNT_INTEGER_DIGITS, AMOUNT_SCALE);
    }
}
