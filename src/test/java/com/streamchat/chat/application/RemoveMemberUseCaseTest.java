package com.streamchat.chat.application;

import com.streamchat.chat.application.port.out.GroupRepository;
import com.streamchat.chat.application.usecase.RemoveMemberUseCaseImpl;
import com.streamchat.chat.domain.exception.GroupNotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoveMemberUseCaseTest {

    private GroupRepository groupRepository;
    private Clock clock;
    private RemoveMemberUseCaseImpl useCase;

    private UUID creatorId;
    private UUID memberId;
    private UUID groupId;
    private ChatGroup group;

    @BeforeEach
    void setUp() {
        groupRepository = mock(GroupRepository.class);
        clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        useCase = new RemoveMemberUseCaseImpl(groupRepository);

        creatorId = UUID.randomUUID();
        memberId  = UUID.randomUUID();
        groupId   = UUID.randomUUID();

        group = ChatGroup.create(creatorId, "Test Group", clock);
        group.addMember(UserId.of(creatorId), clock);
        group.addMember(UserId.of(memberId), clock);

        when(groupRepository.findById(GroupId.of(groupId))).thenReturn(Optional.of(group));
        when(groupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void removes_member_when_requester_is_member() {
        assertThat(group.isMember(UserId.of(memberId))).isTrue();

        useCase.execute(creatorId, groupId, memberId);

        assertThat(group.isMember(UserId.of(memberId))).isFalse();
        verify(groupRepository).save(group);
    }

    @Test
    void throws_when_group_not_found() {
        UUID unknownGroupId = UUID.randomUUID();
        when(groupRepository.findById(GroupId.of(unknownGroupId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(creatorId, unknownGroupId, memberId))
                .isInstanceOf(GroupNotFoundException.class);

        verify(groupRepository, never()).save(any());
    }

    @Test
    void throws_when_requester_is_not_member() {
        UUID outsiderId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.execute(outsiderId, groupId, memberId))
                .isInstanceOf(NotGroupMemberException.class);

        verify(groupRepository, never()).save(any());
    }

    @Test
    void throws_when_trying_to_remove_creator() {
        assertThatThrownBy(() -> useCase.execute(creatorId, groupId, creatorId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("creator");

        verify(groupRepository, never()).save(any());
    }

    @Test
    void removing_nonexistent_member_is_idempotent() {
        UUID nonMemberId = UUID.randomUUID();

        // No lanza excepción — simplemente no hay nada que eliminar
        useCase.execute(creatorId, groupId, nonMemberId);

        // El grupo sigue igual, pero se guardó (el dominio no falla)
        verify(groupRepository).save(group);
        assertThat(group.isMember(UserId.of(nonMemberId))).isFalse();
    }
}
