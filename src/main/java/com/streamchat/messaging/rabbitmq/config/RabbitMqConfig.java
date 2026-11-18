package com.streamchat.messaging.rabbitmq.config;

import com.streamchat.chat.application.port.out.MessageEventPublisher;
import com.streamchat.messaging.rabbitmq.publisher.LoggingPublisher;
import com.streamchat.messaging.rabbitmq.publisher.MetricsPublisher;
import com.streamchat.messaging.rabbitmq.publisher.RabbitMqMessagePublisher;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configura el RabbitTemplate con Jackson y registra la cadena de decoradores
 * sobre el publicador de eventos:
 *
 *   OutboxRelay → LoggingPublisher → MetricsPublisher → RabbitMqMessagePublisher
 *
 * Patrón Decorator: cada capa añade una responsabilidad (logging, métricas)
 * sin modificar el publicador real (SRP + OCP).
 */
@Configuration
public class RabbitMqConfig {

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jacksonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter);
        template.setMandatory(true);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jacksonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jacksonMessageConverter);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(10);
        return factory;
    }

    /**
     * Cadena de decoradores. @Primary hace que Spring inyecte este bean
     * donde se pida MessageEventPublisher (OutboxRelay, etc.).
     *
     * Logging → Metrics → Retrying → RabbitMqMessagePublisher
     */
    @Bean
    @Primary
    public MessageEventPublisher messageEventPublisher(RabbitMqMessagePublisher rabbitMqMessagePublisher,
                                                       MeterRegistry meterRegistry) {
        return new LoggingPublisher(
                new MetricsPublisher(
                        new com.streamchat.messaging.rabbitmq.publisher.RetryingMessageEventPublisher(
                                rabbitMqMessagePublisher),
                        meterRegistry)
        );
    }
}
