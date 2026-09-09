package com.carddemo.pendingauth.jms;

import com.carddemo.pendingauth.service.AuthorizationDriverService;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

/**
 * The MQ end of CP00: 3100-READ-REQUEST-MQ and 7100-SEND-RESPONSE.
 *
 * <p>COPAUA0C is a triggered CICS server that opens
 * {@code AWS.M2.CARDDEMO.PAUTH.REQUEST}, loops on {@code MQGET} and replies with
 * {@code MQPUT1} to the request's {@code MQMD-REPLYTOQ}, syncpointing after each
 * message. Under Artemis the listener container is the trigger and the loop, one
 * transacted message at a time (D-7); the reply goes to {@code JMSReplyTo} when
 * the requester set one, otherwise to the module's documented reply queue. The
 * correlation id is echoed exactly as {@code WS-SAVE-CORRELID} is.
 *
 * <p>The legacy 500-message process limit is a property of a triggered run, not
 * of the contract, so it is not reproduced (BD-7); the quirk that the limit lets
 * a 501st message through is documented in the FR instead.
 */
@Component
@ConditionalOnProperty(prefix = "carddemo.mq", name = "enabled", havingValue = "true")
public class AuthorizationRequestListener {

    private final AuthorizationDriverService driver;
    private final JmsTemplate jmsTemplate;

    public AuthorizationRequestListener(AuthorizationDriverService driver,
                                        JmsTemplate jmsTemplate) {
        this.driver = driver;
        this.jmsTemplate = jmsTemplate;
    }

    @JmsListener(destination = PendingAuthQueues.REQUEST,
            containerFactory = "cardDemoListenerContainerFactory")
    public void onRequest(Message message) throws JMSException {
        if (!(message instanceof TextMessage text)) {
            return;
        }
        String reply = driver.process(text.getText());
        String correlationId = message.getJMSCorrelationID();
        Destination replyTo = message.getJMSReplyTo();
        if (replyTo != null) {
            jmsTemplate.send(replyTo, session -> {
                TextMessage response = session.createTextMessage(reply);
                response.setJMSCorrelationID(correlationId);
                return response;
            });
        } else {
            jmsTemplate.send(PendingAuthQueues.REPLY, session -> {
                TextMessage response = session.createTextMessage(reply);
                response.setJMSCorrelationID(correlationId);
                return response;
            });
        }
    }
}
