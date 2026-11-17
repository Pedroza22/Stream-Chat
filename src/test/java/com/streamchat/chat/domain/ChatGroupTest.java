package com.streamchat.chat.domain;

import com.streamchat.chat.domain.exception.NotGroupMemberException;
import com.streamchat.chat.domain.model.ChatGroup;
import com.streamchat.chat.domain.model.ChatMessage;
import com.streamchat.chat.domain.valueobject.UserId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatGroupTest {

    private final Clock clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void sendMessage_throwsNotGroupMemberException_whenUserNotMember() {
        UUID creatorId = UUID.randomUUID();
        ChatGroup group = ChatGroup.create(creatorId, "Test Group", clock);
        group.addMember(UserId.of(creatorId), clock);

        UUID nonMemberId = UUID.randomUUID();
        assertThatThrownBy(() -> group.sendMessage(UserId.of(nonMemberId), "Hello", clock))
                .isInstanceOf(NotGroupMemberException.class);
    }

    @Test
    void sendMessage_createsMessage_whenUserIsMember() {
        UUID creatorId = UUID.randomUUID();
        ChatGroup group = ChatGroup.create(creatorId, "Test Group", clock);
        group.addMember(UserId.of(creatorId), clock);

        ChatMessage msg = group.sendMessage(UserId.of(creatorId), "Hello", clock);

        assertThat(msg).isNotNull();
        assertThat(msg.getContent()).isEqualTo("Hello");
        assertThat(msg.getSenderId()).isEqualTo(creatorId);
    }

    @Test
    void sendMessage_throwsIllegalArgumentException_whenContentTooLong() {
        UUID creatorId = UUID.randomUUID();
        ChatGroup group = ChatGroup.create(creatorId, "Test Group", clock);
        group.addMember(UserId.of(creatorId), clock);

        String tooLong = "a".repeat(4001);
        assertThatThrownBy(() -> group.sendMessage(UserId.of(creatorId), tooLong, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendMessage_throwsIllegalArgumentException_whenContentBlank() {
        UUID creatorId = UUID.randomUUID();
        ChatGroup group = ChatGroup.create(creatorId, "Test Group", clock);
        group.addMember(UserId.of(creatorId), clock);

        assertThatThrownBy(() -> group.sendMessage(UserId.of(creatorId), "   ", clock))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
