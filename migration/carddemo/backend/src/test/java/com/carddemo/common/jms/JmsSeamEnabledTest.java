package com.carddemo.common.jms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "carddemo.mq.enabled=true")
class JmsSeamEnabledTest {

    @Autowired
    private JmsTemplate cardDemoJmsTemplate;

    @Test
    void jmsTemplateIsTransactedAndUsesTheSharedConverter() {
        assertThat(cardDemoJmsTemplate.isSessionTransacted()).isTrue();
        assertThat(cardDemoJmsTemplate.getMessageConverter()).isNotNull();
    }

    @Test
    void queueNamesMatchTheLegacyLiterals() {
        assertThat(CardDemoQueues.REQUEST).isEqualTo("CARDDEMO.REQUEST.QUEUE");
        assertThat(CardDemoQueues.RESPONSE).isEqualTo("CARDDEMO.RESPONSE.QUEUE");
        assertThat(CardDemoQueues.REPLY_ACCT).isEqualTo("CARD.DEMO.REPLY.ACCT");
        assertThat(CardDemoQueues.REPLY_DATE).isEqualTo("CARD.DEMO.REPLY.DATE");
        assertThat(CardDemoQueues.ERROR).isEqualTo("CARD.DEMO.ERROR");
    }
}
