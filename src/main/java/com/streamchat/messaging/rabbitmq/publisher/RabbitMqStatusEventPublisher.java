package com.streamchat.messaging.rabbitmq.publisher;

import com.streamchat.contracts.ServerEvent;
import com.streamchat.messaging.rabbitmq.config.RabbitMqProperties;
import com.streamchat.presence.application.port.out.StatusEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Adaptador de salida: publica ServerEvent al Fanout Exchange
 * "streamchat.status.updates" de RabbitMQ.
 *
 * El Fanout no necesita routing key — se pasa cadena vacía.
 * Todos los consumidores vinculados al exchange reciben la copia.
 */
@Component
public class RabbitMqStatusEventPublisher implements StatusEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqStatusEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitMqStatusEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(ServerEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqProperties.STATUS_FANOUT_EXCHANGE,
                    "",   // Fanout ignora la routing key
                    event
            );
            log.debug("status_publisher: published event type={} to fanout={}",
                    event.type(), RabbitMqProperties.STATUS_FANOUT_EXCHANGE);
        } catch (Exception e) {
            log.error("status_publisher: failed to publish event type={}", event.type(), e);
            throw e;
        }
    }
}
