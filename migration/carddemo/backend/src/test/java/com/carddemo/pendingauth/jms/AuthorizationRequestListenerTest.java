package com.carddemo.pendingauth.jms;

import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-P10/FR-P11: the MQ end of CP00 over an embedded Artemis broker (D-7) —
 * MQGET on {@code AWS.M2.CARDDEMO.PAUTH.REQUEST}, MQPUT1 to the request's
 * reply-to queue, correlation id echoed.
 */
@SpringBootTest(properties = {
        "carddemo.mq.enabled=true",
        "spring.artemis.mode=embedded",
        "spring.artemis.embedded.enabled=true",
        "spring.jms.listener.auto-startup=true",
        "spring.datasource.url=jdbc:h2:mem:carddemo-cp00-mq;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class AuthorizationRequestListenerTest {

    private static final String CARD_ACCT_1 = "9680294154603697";
    private static final String OWN_REPLY_QUEUE = "AWS.M2.CARDDEMO.PAUTH.REPLY.TEST";

    @Autowired
    private JmsTemplate cardDemoJmsTemplate;

    private static String request(String transactionId) {
        return String.join(",",
                "250115", "150000", CARD_ACCT_1, "0100", "2605", "0100", "POS",
                "001000", "+00000013.75", "5411", "840", "05", "900000001",
                "ACME SUPERMARKET", "NEW YORK", "NY", "10001", transactionId);
    }

    @Test
    void frP10_aRequestIsAnsweredOnItsReplyToQueueWithTheCorrelationIdEchoed() {
        cardDemoJmsTemplate.send(PendingAuthQueues.REQUEST, session -> {
            TextMessage message = session.createTextMessage(request("PAUTHMQ00000001"));
            message.setJMSCorrelationID("CORREL-0001");
            message.setJMSReplyTo(session.createQueue(OWN_REPLY_QUEUE));
            return message;
        });

        cardDemoJmsTemplate.setReceiveTimeout(10_000);
        Message reply = cardDemoJmsTemplate.receive(OWN_REPLY_QUEUE);

        assertThat(reply).isInstanceOf(TextMessage.class);
        assertThat(text(reply))
                .isEqualTo(CARD_ACCT_1 + ",PAUTHMQ00000001,150000,00,0000,         13.75,");
        assertThat(correlationId(reply)).isEqualTo("CORREL-0001");
    }

    @Test
    void frP11_aRequestWithoutAReplyToGoesToTheModulesReplyQueue() {
        cardDemoJmsTemplate.send(PendingAuthQueues.REQUEST, session -> {
            TextMessage message = session.createTextMessage(request("PAUTHMQ00000002"));
            message.setJMSCorrelationID("CORREL-0002");
            return message;
        });

        cardDemoJmsTemplate.setReceiveTimeout(10_000);
        Message reply = cardDemoJmsTemplate.receive(PendingAuthQueues.REPLY);

        assertThat(text(reply)).contains("PAUTHMQ00000002");
        assertThat(correlationId(reply)).isEqualTo("CORREL-0002");
    }

    private static String text(Message message) {
        try {
            return ((TextMessage) message).getText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String correlationId(Message message) {
        try {
            return message.getJMSCorrelationID();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
