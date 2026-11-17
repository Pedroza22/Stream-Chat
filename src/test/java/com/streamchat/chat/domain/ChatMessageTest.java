package com.streamchat.chat.domain;

import com.streamchat.chat.domain.model.ChatMessage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageTest {

    @Test
    void create_setsIdAndCreatedAt() {
        Clock clock = Clock.fixed(Instant.parse("2024-06-15T10:00:00Z"), ZoneOffset.UTC);
        UUID groupId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        ChatMessage msg = ChatMessage.create(groupId, senderId, "Hello World", clock);

        assertThat(msg.getId()).isNotNull();
        assertThat(msg.getCreatedAt()).isEqualTo(Instant.parse("2024-06-15T10:00:00Z"));
        assertThat(msg.getGroupId()).isEqualTo(groupId);
        assertThat(msg.getSenderId()).isEqualTo(senderId);
        assertThat(msg.getContent()).isEqualTo("Hello World");
    }
}
