package com.streamchat.messaging.rabbitmq.consumer;

import com.rabbitmq.client.Channel;
import com.streamchat.chat.application.port.out.ClientNotifier;
import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.contracts.ServerEvent;
import com.streamchat.contracts.TypingUpdatePayload;
import com.streamchat.messaging.rabbitmq.config.RabbitMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * status_backend — Consumidor del Fanout Exchange "streamchat.status.updates".
 *
 * Recibe los cambios de estado de typing publicados por el typing_processor
 * (ProcessActivityUseCaseImpl) y los distribuye a los clientes WebSocket
 * del grupo correspondiente.
 *
 * Flujo del enunciado §Flujo1:
 *   typing_processor → RabbitMQ [streamchat.status.updates] (Fanout)
 *     → StatusUpdatesConsumer (status_backend)
 *       → ClientNotifier → WebSocket → cliente
 *
 * El Fanout garantiza que si hay múltiples instancias del servicio,
 * cada una recibe una copia y puede notificar a sus clientes conectados.
 */
@Component
public class StatusUpdatesConsumer {

    private static final Logger log = LoggerFactory.getLogger(StatusUpdatesConsumer.class);

    private final ClientNotifier clientNotifier;

    public StatusUpdatesConsumer(ClientNotifier clientNotifier) {
        this.clientNotifier = clientNotifier;
    }

    @RabbitListener(queues = RabbitMqProperties.STATUS_UPDATES_QUEUE, ackMode = "MANUAL")
    public void onStatusUpdate(ServerEvent event,
                               Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            if (event == null || event.payload() == null) {
                channel.basicAck(deliveryTag, false);
                return;
            }

            // El payload es un TypingUpdatePayload serializado como LinkedHashMap por Jackson
            // ObjectMapper.convertValue lo convierte al tipo correcto
            TypingUpdatePayload payload = extractPayload(event);
            if (payload != null) {
                GroupId groupId = GroupId.of(payload.groupId());
                clientNotifier.notifyGroup(groupId, event);
                log.debug("status_backend: notified groupId={} userId={} typing={}",
                        payload.groupId(), payload.userId(), payload.typing());
            }

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("status_backend: failed to process status update", e);
            try {
                channel.basicNack(deliveryTag, false, false); // no requeue — evitar loop
            } catch (Exception nackEx) {
                log.error("status_backend: failed to nack", nackEx);
            }
        }
    }

    private TypingUpdatePayload extractPayload(ServerEvent event) {
        if (event.payload() instanceof TypingUpdatePayload p) {
            return p;
        }
        // Jackson deserializa Object como LinkedHashMap — reconstruir manualmente
        if (event.payload() instanceof java.util.Map<?, ?> map) {
            try {
                java.util.UUID groupId = java.util.UUID.fromString(map.get("groupId").toString());
                java.util.UUID userId  = java.util.UUID.fromString(map.get("userId").toString());
                boolean typing = Boolean.parseBoolean(map.get("typing").toString());
                java.time.Instant occurredAt = java.time.Instant.parse(map.get("occurredAt").toString());
                return new TypingUpdatePayload(groupId, userId, typing, occurredAt);
            } catch (Exception e) {
                log.warn("status_backend: could not parse TypingUpdatePayload from map: {}", map);
            }
        }
        return null;
    }
}
