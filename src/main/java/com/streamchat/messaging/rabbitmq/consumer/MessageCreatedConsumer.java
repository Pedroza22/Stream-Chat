package com.streamchat.messaging.rabbitmq.consumer;

import com.rabbitmq.client.Channel;
import com.streamchat.chat.application.port.out.ClientNotifier;
import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.contracts.MessageCreatedEvent;
import com.streamchat.contracts.ServerEvent;
import com.streamchat.messaging.rabbitmq.config.RabbitMqProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumidor idempotente de eventos MESSAGE_CREATED.
 *
 * Recibe el evento desde la cola principal, lo deduplica usando processed_events
 * y notifica a los miembros del grupo conectados por WebSocket vía ClientNotifier (P1).
 *
 * ACK manual: solo confirma al broker cuando el procesamiento fue exitoso.
 * NACK sin requeue: si falla, el mensaje va a la cola de reintento con TTL.
 */
@Component
public class MessageCreatedConsumer extends AbstractIdempotentConsumer<MessageCreatedEvent> {

    private static final String CONSUMER_NAME = "message-created";

    private final ClientNotifier clientNotifier;

    public MessageCreatedConsumer(ProcessedEventRepository processedEventRepository,
                                  ClientNotifier clientNotifier) {
        super(processedEventRepository);
        this.clientNotifier = clientNotifier;
    }

    @Override
    protected String consumerName() {
        return CONSUMER_NAME;
    }

    @Override
    protected UUID extractEventId(MessageCreatedEvent message) {
        return message.eventId();
    }

    @Override
    protected void doProcess(MessageCreatedEvent event) {
        GroupId groupId = GroupId.of(event.groupId());
        ServerEvent serverEvent = new ServerEvent("MESSAGE_NEW", event);
        clientNotifier.notifyGroup(groupId, serverEvent);
    }

    /**
     * Spring llama a este método por cada mensaje en la cola.
     * Delega a handle() de la clase base que aplica el flujo idempotente.
     */
    @RabbitListener(
            queues = RabbitMqProperties.DEFAULT_MSG_QUEUE,
            ackMode = "MANUAL"
    )
    public void onMessage(MessageCreatedEvent event,
                          Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        handle(event, channel, deliveryTag);
    }
}
