package com.carddemo.common.jms;

import jakarta.jms.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

/**
 * The MQ seam (D-7): ActiveMQ Artemis in place of IBM MQ, driven through
 * {@link JmsTemplate} for MQPUT and {@code @JmsListener} for MQGET.
 *
 * <p>Off unless {@code carddemo.mq.enabled=true} so the online app, CI and the
 * batch jobs never need a broker. The MQ streams (S-09, S-10) switch it on in
 * their own profile and add their listeners in their own package; they should
 * not need to change anything here.
 */
@Configuration
@EnableJms
@ConditionalOnProperty(prefix = "carddemo.mq", name = "enabled", havingValue = "true")
public class JmsMessagingConfiguration {

    /**
     * The COBOL servers exchange fixed-layout text on the queues; JSON text is
     * the modern equivalent and keeps the payload readable in the broker console.
     */
    @Bean
    public MessageConverter cardDemoMessageConverter() {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");
        return converter;
    }

    @Bean
    public JmsTemplate cardDemoJmsTemplate(ConnectionFactory connectionFactory,
                                           MessageConverter cardDemoMessageConverter) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setMessageConverter(cardDemoMessageConverter);
        template.setSessionTransacted(true);
        return template;
    }

    /**
     * Transacted, single-threaded consumption, matching the one-message-at-a-time
     * unit of work a triggered CICS MQ server runs under.
     */
    @Bean
    public DefaultJmsListenerContainerFactory cardDemoListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter cardDemoMessageConverter) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(cardDemoMessageConverter);
        factory.setSessionTransacted(true);
        factory.setConcurrency("1");
        return factory;
    }
}
