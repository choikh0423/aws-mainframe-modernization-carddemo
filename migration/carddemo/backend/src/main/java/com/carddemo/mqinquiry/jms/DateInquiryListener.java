package com.carddemo.mqinquiry.jms;

import com.carddemo.common.jms.CardDemoQueues;
import com.carddemo.mqinquiry.service.DateInquiryService;
import com.carddemo.mqinquiry.service.MqInquiryResult;

import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * CDRD (CODATE01) as a triggered MQ server: every message on the queue is
 * answered with the system date and time
 * (app/app-vsam-mq/cbl/CODATE01.cbl:274-361).
 */
@Component
@ConditionalOnProperty(prefix = "carddemo.mq", name = "enabled", havingValue = "true")
public class DateInquiryListener {

    private final DateInquiryService dateInquiry;
    private final MqInquiryReplySender replySender;

    public DateInquiryListener(DateInquiryService dateInquiry, MqInquiryReplySender replySender) {
        this.dateInquiry = dateInquiry;
        this.replySender = replySender;
    }

    @JmsListener(destination = MqInquiryQueues.DATE_REQUEST_PROPERTY,
            containerFactory = "cardDemoListenerContainerFactory")
    public void onRequest(TextMessage message) throws JMSException {
        MqInquiryResult result = dateInquiry.inquire();
        replySender.send(CardDemoQueues.REPLY_DATE, result.payload(), message);
    }
}
