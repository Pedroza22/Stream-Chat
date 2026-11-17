package com.streamchat.chat.application;

import com.streamchat.chat.application.dto.CreateGroupCommand;
import com.streamchat.chat.application.port.out.GroupRepository;
import com.streamchat.chat.application.usecase.CreateGroupUseCaseImpl;
import com.streamchat.chat.domain.model.ChatGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreateGroupUseCaseTest {

    private GroupRepository groupRepository;
    private Clock clock;
    private CreateGroupUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        groupRepository = mock(GroupRepository.class);
        clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        useCase = new CreateGroupUseCaseImpl(groupRepository, clock);
    }

    @Test
    void creator_isAddedAsFirstMember_afterCreate() {
        UUID creatorId = UUID.randomUUID();
        when(groupRepository.save(any(ChatGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatGroup group = useCase.execute(new CreateGroupCommand(creatorId, "My Group"));

        assertThat(group.getMembers()).isNotEmpty();
        boolean creatorIsMember = group.getMembers().stream()
                .anyMatch(m -> m.getUserId().equals(creatorId));
        assertThat(creatorIsMember).isTrue();
    }
}
