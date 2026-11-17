package com.streamchat.chat.application;

import com.streamchat.FakeMessageEventPublisher;
import com.streamchat.chat.application.dto.SendMessageCommand;
import com.streamchat.chat.application.port.out.GroupRepository;
import com.streamchat.chat.application.port.out.MessageRepository;
import com.streamchat.chat.application.usecase.SendMessageUseCaseImpl;
import com.streamchat.chat.domain.exception.NotGroupMemberException;
import com.streamchat.chat.domain.model.ChatGroup;
import com.streamchat.chat.domain.model.ChatMessage;
import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.chat.domain.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SendMessageUseCaseTest {

    private GroupRepository groupRepository;
    private MessageRepository messageRepository;
    private FakeMessageEventPublisher publisher;
    private Clock clock;
    private SendMessageUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        groupRepository = mock(GroupRepository.class);
        messageRepository = mock(MessageRepository.class);
        publisher = new FakeMessageEventPublisher();
        clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        useCase = new SendMessageUseCaseImpl(groupRepository, messageRepository, publisher, clock);
    }

    @Test
    void rejected_whenUserNotMember() {
        UUID groupId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID nonMemberId = UUID.randomUUID();

        ChatGroup group = ChatGroup.create(creatorId, "Test", clock);
        group.addMember(UserId.of(creatorId), clock);

        when(groupRepository.findById(GroupId.of(groupId))).thenReturn(Optional.of(group));

        SendMessageCommand cmd = new SendMessageCommand(nonMemberId, groupId, "Hello");
        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(NotGroupMemberException.class);
    }

    @Test
    void messageSaved_andEventPublished_whenUserIsMember() {
        UUID groupId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();

        ChatGroup group = ChatGroup.create(creatorId, "Test", clock);
        group.addMember(UserId.of(creatorId), clock);

        when(groupRepository.findById(GroupId.of(groupId))).thenReturn(Optional.of(group));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        SendMessageCommand cmd = new SendMessageCommand(creatorId, groupId, "Hello");
        ChatMessage result = useCase.execute(cmd);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("Hello");
        assertThat(publisher.getPublished()).hasSize(1);
        assertThat(publisher.getPublished().get(0).content()).isEqualTo("Hello");
    }
}
