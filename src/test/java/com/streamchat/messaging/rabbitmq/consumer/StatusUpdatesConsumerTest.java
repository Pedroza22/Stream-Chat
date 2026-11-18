package com.streamchat.messaging.rabbitmq.consumer;

import com.rabbitmq.client.Channel;
import com.streamchat.FakeClientNotifier;
import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.contracts.ServerEvent;
import com.streamchat.contracts.TypingUpdatePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios del status_backend (StatusUpdatesConsumer).
 * Verifica que recibe ServerEvent del Fanout RabbitMQ y notifica por WebSocket.
 */
class StatusUpdatesConsumerTest {

    private FakeClientNotifier notifier;
    private Channel channel;
    private StatusUpdatesConsumer consumer;

    @BeforeEach
    void setUp() {
        notifier  = new FakeClientNotifier();
        channel   = mock(Channel.class);
        consumer  = new StatusUpdatesConsumer(notifier);
    }

    @Test
    void notifies_group_when_typing_update_received() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID userId  = UUID.randomUUID();
        TypingUpdatePayload payload = new TypingUpdatePayload(groupId, userId, true, Instant.now());
        ServerEvent event = new ServerEvent("TYPING_UPDATE", payload);

        consumer.onStatusUpdate(event, channel, 1L);

        assertThat(notifier.getNotifications()).hasSize(1);
        assertThat(notifier.getNotifications().get(0).getKey())
                .isEqualTo(GroupId.of(groupId));
        verify(channel).basicAck(1L, false);
    }

    @Test
    void acks_and_does_nothing_for_null_event() throws Exception {
        consumer.onStatusUpdate(null, channel, 2L);

        assertThat(notifier.getNotifications()).isEmpty();
        verify(channel).basicAck(2L, false);
    }

    @Test
    void acks_and_does_nothing_for_null_payload() throws Exception {
        ServerEvent event = new ServerEvent("TYPING_UPDATE", null);

        consumer.onStatusUpdate(event, channel, 3L);

        assertThat(notifier.getNotifications()).isEmpty();
        verify(channel).basicAck(3L, false);
    }

    @Test
    void notifies_stopped_typing() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID userId  = UUID.randomUUID();
        TypingUpdatePayload payload = new TypingUpdatePayload(groupId, userId, false, Instant.now());
        ServerEvent event = new ServerEvent("TYPING_UPDATE", payload);

        consumer.onStatusUpdate(event, channel, 4L);

        TypingUpdatePayload received = (TypingUpdatePayload)
                notifier.getNotifications().get(0).getValue().payload();
        assertThat(received.typing()).isFalse();
        assertThat(received.userId()).isEqualTo(userId);
    }
}
