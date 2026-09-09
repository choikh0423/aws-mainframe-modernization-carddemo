package com.carddemo.common.jms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JmsSeamDisabledTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void seamBeansAreAbsentUntilAnMqStreamEnablesThem() {
        assertThat(context.containsBean("cardDemoJmsTemplate")).isFalse();
        assertThat(context.containsBean("cardDemoListenerContainerFactory")).isFalse();
    }
}
