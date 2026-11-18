package com.streamchat.messaging.kafka.consumer;

import com.streamchat.chat.application.port.out.MessageRepository;
import com.streamchat.chat.domain.model.ChatMessage;
import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.contracts.MessageCreatedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios del analytics_service (MessageAuditConsumer).
 * Verifica persistencia en PostgreSQL e idempotencia.
 */
class MessageAuditConsumerTest {

    private MessageRepository messageRepository;
    private Acknowledgment ack;
    private MessageAuditConsumer consumer;

    private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        messageRepository = mock(MessageRepository.class);
        ack               = mock(Acknowledgment.class);
        consumer = new MessageAuditConsumer(
                messageRepository,
                Clock.fixed(T0, ZoneOffset.UTC),
                new SimpleMeterRegistry());
    }

    private MessageCreatedEvent event(UUID groupId, UUID messageId) {
        return new MessageCreatedEvent(
                UUID.randomUUID(), "MESSAGE_CREATED", messageId,
                groupId, UUID.randomUUID(), "Hola mundo",
                T0, UUID.randomUUID(), 1);
    }

    @Test
    void persists_new_message_and_acknowledges() {
        UUID groupId   = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        MessageCreatedEvent evt = event(groupId, messageId);

        when(messageRepository.findByGroupId(GroupId.of(groupId), 0, Integer.MAX_VALUE))
                .thenReturn(List.of());
        when(messageRepository.countByGroupId(any())).thenReturn(1L);

        consumer.onAuditEvent(evt, ack);

        verify(messageRepository).save(any(ChatMessage.class));
        verify(ack).acknowledge();
    }

    @Test
    void skips_duplicate_message_and_acknowledges() {
        UUID groupId   = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        MessageCreatedEvent evt = event(groupId, messageId);

        // Simular que el mensaje ya existe
        ChatMessage existing = ChatMessage.create(
                groupId, UUID.randomUUID(), "Hola mundo",
                Clock.fixed(T0, ZoneOffset.UTC));
        // Forzar el mismo id que el evento
        ChatMessage withSameId = mock(ChatMessage.class);
        when(withSameId.getId()).thenReturn(messageId);

        when(messageRepository.findByGroupId(GroupId.of(groupId), 0, Integer.MAX_VALUE))
                .thenReturn(List.of(withSameId));

        consumer.onAuditEvent(evt, ack);

        verify(messageRepository, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void does_not_acknowledge_on_repository_error() {
        UUID groupId = UUID.randomUUID();
        MessageCreatedEvent evt = event(groupId, UUID.randomUUID());

        when(messageRepository.findByGroupId(any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("DB error"));

        consumer.onAuditEvent(evt, ack);

        verify(messageRepository, never()).save(any());
        verify(ack, never()).acknowledge();
    }
}
