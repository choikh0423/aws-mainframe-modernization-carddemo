package com.carddemo.mqinquiry.jms;

import com.carddemo.common.jms.CardDemoQueues;
import com.carddemo.mqinquiry.message.InquiryRequestMessage;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CDRA and CDRD over the Artemis seam (B-05 / D-7): FR-MQI-003, FR-MQI-030..033.
 */
@SpringBootTest(properties = {
        "carddemo.mq.enabled=true",
        "spring.artemis.mode=embedded",
        "spring.artemis.embedded.persistent=false",
        "spring.artemis.embedded.queues=CARDDEMO.REQUEST.QUEUE,CARDDEMO.DATE.REQUEST.QUEUE,"
                + "CARD.DEMO.REPLY.ACCT,CARD.DEMO.REPLY.DATE,CARD.DEMO.ERROR",
        "spring.jms.listener.auto-startup=true"
})
class MqInquiryListenerIntegrationTest {

    @Autowired
    @Qualifier("cardDemoJmsTemplate")
    private JmsTemplate jmsTemplate;

    @BeforeEach
    void allowTheBrokerTimeToDeliver() {
        jmsTemplate.setReceiveTimeout(10_000L);
    }

    @Test
    void accountRequestsAreAnsweredOnTheAccountReplyQueue() throws JMSException {
        put(MqInquiryQueues.ACCOUNT_REQUEST_DEFAULT, InquiryRequestMessage.render("INQA", 1L), "CDRA-1");

        TextMessage reply = (TextMessage) jmsTemplate.receive(CardDemoQueues.REPLY_ACCT);

        assertThat(reply).isNotNull();
        assertThat(reply.getJMSCorrelationID()).isEqualTo("CDRA-1");
        assertThat(reply.getText()).hasSize(1000)
                .startsWith("ACCOUNT ID : 00000000001ACCOUNT STATUS : Y");
    }

    @Test
    void anUnknownAccountIsAnsweredOnTheSameQueue() throws JMSException {
        put(MqInquiryQueues.ACCOUNT_REQUEST_DEFAULT, InquiryRequestMessage.render("INQA", 99999999999L),
                "CDRA-2");

        TextMessage reply = (TextMessage) jmsTemplate.receive(CardDemoQueues.REPLY_ACCT);

        assertThat(reply).isNotNull();
        assertThat(reply.getText().stripTrailing())
                .isEqualTo("INVALID REQUEST PARAMETERS ACCT ID : 99999999999");
    }

    @Test
    void dateRequestsAreAnsweredOnTheDateReplyQueue() throws JMSException {
        put(MqInquiryQueues.DATE_REQUEST_DEFAULT, InquiryRequestMessage.render("DATE", 0L), "CDRD-1");

        TextMessage reply = (TextMessage) jmsTemplate.receive(CardDemoQueues.REPLY_DATE);

        assertThat(reply).isNotNull();
        assertThat(reply.getJMSCorrelationID()).isEqualTo("CDRD-1");
        assertThat(reply.getText()).hasSize(1000)
                .matches("SYSTEM DATE : \\d{2}-\\d{2}-\\d{4}SYSTEM TIME : \\d{2}:\\d{2}:\\d{2} *");
    }

    @Test
    void requestsAreAnsweredOneAtATimeInQueueOrder() throws JMSException {
        for (long accountId = 1; accountId <= 5; accountId++) {
            put(MqInquiryQueues.ACCOUNT_REQUEST_DEFAULT, InquiryRequestMessage.render("INQA", accountId),
                    "CDRA-ORDER-" + accountId);
        }

        for (long accountId = 1; accountId <= 5; accountId++) {
            Message reply = jmsTemplate.receive(CardDemoQueues.REPLY_ACCT);
            assertThat(reply).isNotNull();
            assertThat(reply.getJMSCorrelationID()).isEqualTo("CDRA-ORDER-" + accountId);
            assertThat(((TextMessage) reply).getText())
                    .startsWith("ACCOUNT ID : " + String.format("%011d", accountId));
        }
    }

    /** FR-MQI-003: with no correlation id on the request, the message id is echoed. */
    @Test
    void aRequestWithoutACorrelationIdIsAnsweredWithItsMessageId() throws JMSException {
        String payload = InquiryRequestMessage.render("INQA", 2L);
        jmsTemplate.send(MqInquiryQueues.ACCOUNT_REQUEST_DEFAULT,
                session -> session.createTextMessage(payload));

        TextMessage reply = (TextMessage) jmsTemplate.receive(CardDemoQueues.REPLY_ACCT);

        assertThat(reply).isNotNull();
        assertThat(reply.getJMSCorrelationID()).startsWith("ID:");
    }

    private void put(String destination, String payload, String correlationId) {
        jmsTemplate.send(destination, session -> {
            TextMessage message = session.createTextMessage(payload);
            message.setJMSCorrelationID(correlationId);
            return message;
        });
    }
}
