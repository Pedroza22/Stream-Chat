package com.streamchat.messaging.rabbitmq.publisher;

import com.streamchat.contracts.MessageCreatedEvent;
import com.streamchat.messaging.rabbitmq.config.RabbitMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Publica eventos MessageCreatedEvent en RabbitMQ usando publisher confirms.
 *
 * Publisher confirms: el broker confirma (ACK) o rechaza (NACK) cada mensaje
 * publicado. Si el broker no confirma en el timeout, se lanza una excepción
 * para que el relay marque el evento como FAILED y reintente más tarde.
 */
@Component
public class RabbitMqMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqMessagePublisher.class);
    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private final RabbitTemplate rabbitTemplate;

    public RabbitMqMessagePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publica el evento y espera el ACK del broker.
     *
     * @throws PublishException si el broker rechaza o no confirma en el timeout.
     */
    public void publish(MessageCreatedEvent event) {
        CorrelationData correlationData = new CorrelationData(event.eventId().toString());

        rabbitTemplate.convertAndSend(
                RabbitMqProperties.DEFAULT_EXCHANGE,
                RabbitMqProperties.DEFAULT_MSG_ROUTING_KEY,
                event,
                correlationData
        );

        waitForConfirm(correlationData, event.eventId());
    }

    private void waitForConfirm(CorrelationData correlationData, UUID eventId) {
        try {
            CorrelationData.Confirm confirm = correlationData
                    .getFuture()
                    .get(CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (confirm == null || !confirm.isAck()) {
                String reason = confirm != null ? confirm.getReason() : "timeout sin respuesta";
                log.warn("Broker rechazó el evento eventId={} reason={}", eventId, reason);
                throw new PublishException("Broker NACK para eventId=" + eventId + ": " + reason);
            }

            log.debug("Broker confirmó eventId={}", eventId);

        } catch (PublishException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error esperando confirm para eventId={}", eventId, e);
            throw new PublishException("Error en publisher confirm para eventId=" + eventId, e);
        }
    }
}
