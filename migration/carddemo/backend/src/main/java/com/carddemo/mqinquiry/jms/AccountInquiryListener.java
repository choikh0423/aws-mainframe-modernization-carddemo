package com.carddemo.mqinquiry.jms;

import com.carddemo.common.jms.CardDemoQueues;
import com.carddemo.mqinquiry.message.InquiryRequestMessage;
import com.carddemo.mqinquiry.service.AccountInquiryService;
import com.carddemo.mqinquiry.service.MqInquiryResult;

import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * CDRA (COACCT01) as a triggered MQ server: one request at a time, each
 * consumed, served and replied to in a single unit of work
 * (app/app-vsam-mq/cbl/COACCT01.cbl:325-388).
 */
@Component
@ConditionalOnProperty(prefix = "carddemo.mq", name = "enabled", havingValue = "true")
public class AccountInquiryListener {

    private final AccountInquiryService accountInquiry;
    private final MqInquiryReplySender replySender;
    private final String requestQueueName;

    public AccountInquiryListener(AccountInquiryService accountInquiry, MqInquiryReplySender replySender,
            @Value(MqInquiryQueues.ACCOUNT_REQUEST_PROPERTY) String requestQueueName) {
        this.accountInquiry = accountInquiry;
        this.replySender = replySender;
        this.requestQueueName = requestQueueName;
    }

    @JmsListener(destination = MqInquiryQueues.ACCOUNT_REQUEST_PROPERTY,
            containerFactory = "cardDemoListenerContainerFactory")
    public void onRequest(TextMessage message) throws JMSException {
        InquiryRequestMessage request = InquiryRequestMessage.parse(message.getText());
        MqInquiryResult result = accountInquiry.inquire(request, requestQueueName);
        String destination = result.errorReport() ? CardDemoQueues.ERROR : CardDemoQueues.REPLY_ACCT;
        replySender.send(destination, result.payload(), message);
    }
}
