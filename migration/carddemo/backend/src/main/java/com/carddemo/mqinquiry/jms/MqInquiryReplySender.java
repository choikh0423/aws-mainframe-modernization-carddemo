package com.carddemo.mqinquiry.jms;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

/**
 * 4100-PUT-REPLY / 9000-ERROR: puts a 1000-byte message on a queue under the
 * unit of work of the request that caused it, echoing the request's ids
 * (COACCT01.cbl:462-486, :501-523).
 */
@Component
@ConditionalOnProperty(prefix = "carddemo.mq", name = "enabled", havingValue = "true")
public class MqInquiryReplySender {

    private final JmsTemplate jmsTemplate;

    public MqInquiryReplySender(@Qualifier("cardDemoJmsTemplate") JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    /**
     * @param request the message being answered; MQMD-MSGID and MQMD-CORRELID
     *                are moved from the saved request ids before the MQPUT
     *                (COACCT01.cbl:469-470)
     */
    public void send(String destination, String payload, Message request) throws JMSException {
        String correlationId = correlationIdOf(request);
        jmsTemplate.send(destination, session -> {
            TextMessage reply = session.createTextMessage(payload);
            reply.setJMSCorrelationID(correlationId);
            return reply;
        });
    }

    private static String correlationIdOf(Message request) throws JMSException {
        String correlationId = request.getJMSCorrelationID();
        return correlationId != null ? correlationId : request.getJMSMessageID();
    }
}
