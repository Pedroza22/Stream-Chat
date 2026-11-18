package com.streamchat.messaging.rabbitmq.consumer;

import com.rabbitmq.client.Channel;
import com.streamchat.contracts.MessageCreatedEvent;
import com.streamchat.messaging.rabbitmq.config.RabbitMqProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios del audit_bridge.
 * Verifica que reenvía correctamente a Kafka y maneja errores.
 */
class AuditBridgeConsumerTest {

    private KafkaTemplate<String, MessageCreatedEvent> kafkaTemplate;
    private Channel channel;
    private AuditBridgeConsumer consumer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        channel       = mock(Channel.class);
        consumer      = new AuditBridgeConsumer(kafkaTemplate);
    }

    private MessageCreatedEvent event(UUID groupId) {
        return new MessageCreatedEvent(
                UUID.randomUUID(), "MESSAGE_CREATED", UUID.randomUUID(),
                groupId, UUID.randomUUID(), "Hola", Instant.now(),
                UUID.randomUUID(), 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void forwards_event_to_kafka_and_acks() throws Exception {
        UUID groupId = UUID.randomUUID();
        MessageCreatedEvent evt = event(groupId);

        when(kafkaTemplate.send(
                eq(RabbitMqProperties.AUDIT_TOPIC),
                eq(groupId.toString()),
                eq(evt)))
            .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        consumer.onMessage(evt, channel, 1L);

        verify(kafkaTemplate).send(RabbitMqProperties.AUDIT_TOPIC, groupId.toString(), evt);
        verify(channel).basicAck(1L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void on_kafka_failure_nacks_with_requeue() throws Exception {
        MessageCreatedEvent evt = event(UUID.randomUUID());

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Kafka down"));

        consumer.onMessage(evt, channel, 2L);

        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel).basicNack(2L, false, true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void uses_groupId_as_partition_key() throws Exception {
        UUID groupId = UUID.randomUUID();
        MessageCreatedEvent evt = event(groupId);

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        consumer.onMessage(evt, channel, 3L);

        // La clave de partición debe ser el groupId para garantizar orden por grupo
        verify(kafkaTemplate).send(
                RabbitMqProperties.AUDIT_TOPIC,
                groupId.toString(),
                evt);
    }
}
