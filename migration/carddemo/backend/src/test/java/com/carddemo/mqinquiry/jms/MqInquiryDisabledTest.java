package com.carddemo.mqinquiry.jms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-MQI-032, FR-MQI-033: queue names, and no MQ servers without the seam. */
@SpringBootTest
class MqInquiryDisabledTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void theInquiryServersAreAbsentUnlessTheMqSeamIsEnabled() {
        assertThat(context.getBeanNamesForType(AccountInquiryListener.class)).isEmpty();
        assertThat(context.getBeanNamesForType(DateInquiryListener.class)).isEmpty();
        assertThat(context.getBeanNamesForType(MqInquiryReplySender.class)).isEmpty();
    }

    @Test
    void theRequestQueueDefaultsAreTheDefinedCardDemoQueues() {
        assertThat(MqInquiryQueues.ACCOUNT_REQUEST_DEFAULT).isEqualTo("CARDDEMO.REQUEST.QUEUE");
        assertThat(MqInquiryQueues.DATE_REQUEST_DEFAULT).isEqualTo("CARDDEMO.DATE.REQUEST.QUEUE");
    }
}
