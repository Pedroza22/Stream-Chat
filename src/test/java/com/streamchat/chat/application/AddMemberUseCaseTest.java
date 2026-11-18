package com.streamchat.chat.application;

import com.streamchat.chat.application.dto.AddMemberCommand;
import com.streamchat.chat.application.port.out.GroupRepository;
import com.streamchat.chat.application.usecase.AddMemberUseCaseImpl;
import com.streamchat.chat.domain.exception.NotGroupMemberException;
import com.streamchat.chat.domain.model.ChatGroup;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AddMemberUseCaseTest {

    private GroupRepository groupRepository;
    private Clock clock;
    private AddMemberUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        groupRepository = mock(GroupRepository.class);
        clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        useCase = new AddMemberUseCaseImpl(groupRepository, clock);
    }

    @Test
    void rejected_whenRequesterNotMember() {
        UUID groupId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        ChatGroup group = ChatGroup.create(creatorId, "Test", clock);
        when(groupRepository.findById(GroupId.of(groupId))).thenReturn(Optional.of(group));

        AddMemberCommand cmd = new AddMemberCommand(requesterId, groupId, targetId);
        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(NotGroupMemberException.class);

        verify(groupRepository, never()).save(any(ChatGroup.class));
    }

    @Test
    void targetAdded_whenRequesterIsMember() {
        UUID groupId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        ChatGroup group = ChatGroup.create(creatorId, "Test", clock);
        group.addMember(UserId.of(requesterId), clock);
        when(groupRepository.findById(GroupId.of(groupId))).thenReturn(Optional.of(group));
        when(groupRepository.save(any(ChatGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        AddMemberCommand cmd = new AddMemberCommand(requesterId, groupId, targetId);
        assertThatCode(() -> useCase.execute(cmd)).doesNotThrowAnyException();

        verify(groupRepository).save(group);
        boolean targetIsMember = group.getMembers().stream()
                .anyMatch(m -> m.getUserId().equals(targetId));
        assertThat(targetIsMember).isTrue();
    }
}
