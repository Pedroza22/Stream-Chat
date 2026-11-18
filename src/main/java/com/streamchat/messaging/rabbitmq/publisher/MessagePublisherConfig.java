package com.streamchat.messaging.rabbitmq.publisher;

import com.streamchat.chat.application.port.out.MessageEventPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Ensambla la cadena de decoradores del publicador RabbitMQ:
 * Logging → Metrics → Retrying → RabbitMqMessagePublisher
 *
 * El bean resultante es el {@link Primary} de tipo {@link MessageEventPublisher}.
 */
@Configuration
public class MessagePublisherConfig {

    @Bean
    @Primary
    public MessageEventPublisher messageEventPublisher(RabbitMqMessagePublisher rabbitMqPublisher,
                                                       MeterRegistry meterRegistry) {
        MessageEventPublisher retrying = new RetryingMessageEventPublisher(rabbitMqPublisher);
        MessageEventPublisher metrics = new MetricsMessageEventPublisher(retrying, meterRegistry);
        return new LoggingMessageEventPublisher(metrics);
    }
}
