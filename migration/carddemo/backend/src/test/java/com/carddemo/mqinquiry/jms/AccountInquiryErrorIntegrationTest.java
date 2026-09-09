package com.carddemo.mqinquiry.jms;

import com.carddemo.common.jms.CardDemoQueues;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.mqinquiry.message.InquiryRequestMessage;

import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jms.core.JmsTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;

/** FR-MQI-015: a failed ACCTDAT read reports on CARD.DEMO.ERROR and does not reply. */
@SpringBootTest(properties = {
        "carddemo.mq.enabled=true",
        "spring.artemis.mode=embedded",
        "spring.artemis.embedded.persistent=false",
        "spring.artemis.embedded.queues=CARDDEMO.REQUEST.QUEUE,CARDDEMO.DATE.REQUEST.QUEUE,"
                + "CARD.DEMO.REPLY.ACCT,CARD.DEMO.REPLY.DATE,CARD.DEMO.ERROR",
        "spring.jms.listener.auto-startup=true"
})
class AccountInquiryErrorIntegrationTest {

    @Autowired
    @Qualifier("cardDemoJmsTemplate")
    private JmsTemplate jmsTemplate;

    @MockBean
    private AccountRepository accounts;

    @BeforeEach
    void acctdatIsUnreadable() {
        jmsTemplate.setReceiveTimeout(10_000L);
        Mockito.when(accounts.findById(anyLong())).thenThrow(new QueryTimeoutException("ACCTDAT"));
    }

    @Test
    void putsTheErrorReportOnTheErrorQueueAndNothingOnTheReplyQueue() throws JMSException {
        String payload = InquiryRequestMessage.render("INQA", 1L);
        jmsTemplate.send(MqInquiryQueues.ACCOUNT_REQUEST_DEFAULT,
                session -> session.createTextMessage(payload));

        TextMessage report = (TextMessage) jmsTemplate.receive(CardDemoQueues.ERROR);

        assertThat(report).isNotNull();
        assertThat(report.getText()).hasSize(1000);
        assertThat(report.getText().substring(27, 52)).isEqualTo("ERROR WHILE READING ACCTF");
        assertThat(report.getText().substring(65, 113).stripTrailing())
                .isEqualTo(MqInquiryQueues.ACCOUNT_REQUEST_DEFAULT);

        jmsTemplate.setReceiveTimeout(500L);
        assertThat(jmsTemplate.receive(CardDemoQueues.REPLY_ACCT)).isNull();
    }
}
