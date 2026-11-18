package com.streamchat.messaging.rabbitmq.consumer;

import com.rabbitmq.client.Channel;
import com.streamchat.contracts.MessageCreatedEvent;
import com.streamchat.messaging.rabbitmq.config.RabbitMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * audit_bridge — Patrón "Doble Puente" (enunciado §Arquitectura).
 *
 * Consume mensajes de chat del Topic Exchange de RabbitMQ
 * (cola "streamchat.audit.bridge", binding key "message.#")
 * y los reenvía al topic de Kafka "message_audit_log" para
 * auditoría e historial inmutable.
 *
 * Clave de partición: groupId — garantiza orden por grupo en Kafka.
 *
 * Flujo:
 *   RabbitMQ [streamchat.events / message.#]
 *     → AuditBridgeConsumer
 *       → Kafka [message_audit_log]
 *         → MessageAuditConsumer (analytics_service)
 *           → PostgreSQL
 */
@Component
public class AuditBridgeConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditBridgeConsumer.class);

    private final KafkaTemplate<String, MessageCreatedEvent> kafkaTemplate;

    public AuditBridgeConsumer(KafkaTemplate<String, MessageCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @RabbitListener(queues = "streamchat.audit.bridge", ackMode = "MANUAL")
    public void onMessage(MessageCreatedEvent event,
                          Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            // Clave de partición: groupId garantiza orden por grupo
            String partitionKey = event.groupId().toString();
            kafkaTemplate.send(RabbitMqProperties.AUDIT_TOPIC, partitionKey, event);
            log.debug("audit_bridge: forwarded eventId={} groupId={} to Kafka topic={}",
                    event.eventId(), event.groupId(), RabbitMqProperties.AUDIT_TOPIC);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("audit_bridge: failed to forward eventId={}", event.eventId(), e);
            try {
                channel.basicNack(deliveryTag, false, true); // requeue para reintento
            } catch (Exception nackEx) {
                log.error("audit_bridge: failed to nack deliveryTag={}", deliveryTag, nackEx);
            }
        }
    }
}
