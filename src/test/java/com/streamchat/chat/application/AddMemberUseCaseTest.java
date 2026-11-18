package com.streamchat.chat.application;

import com.streamchat.chat.application.dto.AddMemberCommand;
import com.streamchat.chat.application.port.out.GroupRepository;
import com.streamchat.chat.application.usecase.AddMemberUseCaseImpl;
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
import static org.mockito.Mockito.*;

class AddMemberUseCaseTest {

    private GroupRepository groupRepository;
    private Clock clock;
    private AddMemberUseCaseImpl useCase;

    private UUID creatorId;
    private UUID newMemberId;
    private UUID groupId;
    private ChatGroup group;

    @BeforeEach
    void setUp() {
        groupRepository = mock(GroupRepository.class);
        clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        useCase = new AddMemberUseCaseImpl(groupRepository, clock);

        creatorId   = UUID.randomUUID();
        newMemberId = UUID.randomUUID();
        groupId     = UUID.randomUUID();

        group = ChatGroup.create(creatorId, "Test Group", clock);
        group.addMember(UserId.of(creatorId), clock);

        when(groupRepository.findById(GroupId.of(groupId))).thenReturn(Optional.of(group));
        when(groupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void adds_member_when_requester_is_already_member() {
        assertThat(group.isMember(UserId.of(newMemberId))).isFalse();

        useCase.execute(new AddMemberCommand(creatorId, groupId, newMemberId));

        assertThat(group.isMember(UserId.of(newMemberId))).isTrue();
        verify(groupRepository).save(group);
    }

    @Test
    void adding_existing_member_is_idempotent() {
        // El creador ya es miembro — agregarlo de nuevo no duplica
        int membersBefore = group.getMembers().size();

        useCase.execute(new AddMemberCommand(creatorId, groupId, creatorId));

        assertThat(group.getMembers()).hasSize(membersBefore);
        verify(groupRepository).save(group);
    }

    @Test
    void throws_when_group_not_found() {
        UUID unknownGroupId = UUID.randomUUID();
        when(groupRepository.findById(GroupId.of(unknownGroupId))).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                useCase.execute(new AddMemberCommand(creatorId, unknownGroupId, newMemberId)))
                .isInstanceOf(GroupNotFoundException.class);

        verify(groupRepository, never()).save(any());
    }

    @Test
    void throws_when_requester_is_not_member() {
        UUID outsiderId = UUID.randomUUID();

        assertThatThrownBy(() ->
                useCase.execute(new AddMemberCommand(outsiderId, groupId, newMemberId)))
                .isInstanceOf(NotGroupMemberException.class);

        verify(groupRepository, never()).save(any());
    }
}
